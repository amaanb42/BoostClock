package org.fossify.clock.voice.qualification

import org.fossify.clock.voice.RecognitionHypothesis
import org.fossify.clock.voice.VoiceCommandNormalizer
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.ceil
import kotlin.coroutines.startCoroutine
import kotlin.system.exitProcess

private data class Clip(
    val id: String,
    val category: String,
    val expected: String,
    val path: File,
    val speaker: String,
    val condition: String,
)

private data class ResultRow(val clip: Clip, val text: String, val confidence: Float, val latencyMs: Long)

fun main(args: Array<String>) {
    val options = args.toList().chunked(2).associate { pair ->
        pair.first() to pair.getOrElse(1) { error("Missing value for ${pair.first()}") }
    }
    if ("--help" in args || args.isEmpty()) usage()
    val candidate = Candidate.parse(options.getValue("--candidate"))
    val corpus = File(options.getValue("--corpus"))
    val runner = options.getValue("--runner").split(' ').filter(String::isNotBlank)
    val threshold = options["--threshold"]?.toFloat() ?: 0f
    val clips = readManifest(corpus)
    require(clips.isNotEmpty()) { "Corpus is empty" }
    val engine = RunnerEngine(candidate, runner)
    check(runSuspend { engine.prepare() }.isSuccess)
    val rows = clips.map { clip ->
        var hypothesis = RecognitionHypothesis("", 0f, true)
        check(engine.start(onHypothesis = { hypothesis = it }).isSuccess)
        val samples = readWav(clip.path)
        samples.asList().chunked(1_600).forEach { chunk ->
            engine.acceptPcm16(chunk.toShortArray(), chunk.size)
        }
        engine.stop()
        ResultRow(clip, hypothesis.text, hypothesis.confidence, engine.lastInferenceMillis)
    }
    engine.close()
    printReport(candidate, threshold, rows)
}

private fun usage(): Nothing {
    System.err.println("Usage: :voice-qualification:run --args='--candidate moonshine --corpus manifest.tsv --runner /path/to/runner --threshold 0.8'")
    exitProcess(2)
}

private fun readManifest(file: File): List<Clip> = file.readLines().filter { it.isNotBlank() && !it.startsWith("#") }.mapIndexed { index, line ->
    val field = line.split('\t')
    require(field.size == 6) { "${file.path}:${index + 1}: expected id, category, expected, wav, speaker, condition" }
    Clip(field[0], field[1], field[2], file.resolveSibling(field[3]), field[4], field[5])
}

private fun readWav(file: File): ShortArray {
    val bytes = file.readBytes()
    require(bytes.size >= 44 && String(bytes, 0, 4) == "RIFF" && String(bytes, 8, 4) == "WAVE") { "${file.path}: not WAV" }
    val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
    require(buffer.getShort(22).toInt() == 1 && buffer.getInt(24) == 16_000 && buffer.getShort(34).toInt() == 16) {
        "${file.path}: expected 16 kHz mono PCM16"
    }
    var offset = 12
    while (offset + 8 <= bytes.size) {
        val size = buffer.getInt(offset + 4)
        if (String(bytes, offset, 4) == "data") {
            return ShortArray(size / 2) { buffer.getShort(offset + 8 + it * 2) }
        }
        offset += 8 + size + (size and 1)
    }
    error("${file.path}: missing data chunk")
}

private fun printReport(candidate: Candidate, threshold: Float, rows: List<ResultRow>) {
    fun accepted(row: ResultRow) = VoiceCommandNormalizer.commandFor(
        RecognitionHypothesis(row.text, row.confidence, true), threshold,
    )?.name?.lowercase()
    fun recall(category: String): Double {
        val relevant = rows.filter { it.clip.category == category }
        return if (relevant.isEmpty()) Double.NaN else relevant.count { accepted(it) == it.clip.expected }.toDouble() / relevant.size
    }
    val negatives = rows.filter { it.clip.category == "negative" }.count { accepted(it) != null }
    val latencies = rows.map { it.latencyMs }.sorted()
    val p95 = latencies[(ceil(latencies.size * .95).toInt() - 1).coerceAtLeast(0)]
    val normalRecall = recall("normal_command")
    val whisperRecall = recall("whisper_command")
    val normalWer = wer(rows.filter { it.clip.category == "normal_wer" })
    val whisperWer = wer(rows.filter { it.clip.category == "whisper_wer" })
    println("candidate\tnormal_recall\twhisper_recall\tfalse_actions\tnormal_wer\twhisper_wer\tp95_latency_ms")
    println("${candidate.cliName}\t$normalRecall\t$whisperRecall\t$negatives\t$normalWer\t$whisperWer\t$p95")
    rows.forEach { println("clip\t${it.clip.id}\t${it.clip.category}\t${it.text.replace('\t', ' ')}\t${it.confidence}\t${it.latencyMs}") }
}

private fun wer(rows: List<ResultRow>): Double {
    if (rows.isEmpty()) return Double.NaN
    var edits = 0
    var words = 0
    rows.forEach { row ->
        val expected = row.clip.expected.lowercase().trim().split(Regex("\\s+")).filter(String::isNotEmpty)
        val actual = row.text.lowercase().trim().split(Regex("\\s+")).filter(String::isNotEmpty)
        edits += distance(expected, actual)
        words += expected.size
    }
    return edits.toDouble() / words.coerceAtLeast(1)
}

private fun distance(a: List<String>, b: List<String>): Int {
    var previous = IntArray(b.size + 1) { it }
    a.forEachIndexed { i, word ->
        val current = IntArray(b.size + 1)
        current[0] = i + 1
        b.forEachIndexed { j, other ->
            current[j + 1] = minOf(
                current[j] + 1,
                previous[j + 1] + 1,
                previous[j] + if (word == other) 0 else 1,
            )
        }
        previous = current
    }
    return previous.last()
}

private fun <T> runSuspend(block: suspend () -> T): T {
    var outcome: Result<T>? = null
    block.startCoroutine(object : kotlin.coroutines.Continuation<T> {
        override val context = kotlin.coroutines.EmptyCoroutineContext
        override fun resumeWith(result: Result<T>) {
            outcome = result
        }
    })
    return outcome!!.getOrThrow()
}
