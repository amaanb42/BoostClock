package org.fossify.clock.voice.qualification

import org.fossify.clock.voice.RecognitionHypothesis
import org.fossify.clock.voice.VoiceRecognitionEngine
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

enum class Candidate(val cliName: String) {
    MOONSHINE("moonshine"),
    WHISPER("whisper"),
    ZIPFORMER("zipformer");

    companion object {
        fun parse(value: String) = entries.firstOrNull { it.cliName == value }
            ?: error("Unknown candidate '$value'; use moonshine, whisper, or zipformer")
    }
}

/**
 * Adapter for candidate-specific native runners. Each runner receives a 16 kHz mono PCM WAV on
 * stdin and must print one TSV line: final text, confidence, inference milliseconds. Keeping the
 * native commands outside this module lets the same harness run host builds and arm64 adb wrappers.
 */
class RunnerEngine(
    private val candidate: Candidate,
    private val runnerCommand: List<String>,
) : VoiceRecognitionEngine {
    private var callback: ((RecognitionHypothesis) -> Unit)? = null
    private var pcm = ByteArrayOutputStream()
    var lastInferenceMillis: Long = 0
        private set

    override suspend fun prepare(): Result<Unit> = runCatching {
        require(runnerCommand.isNotEmpty()) { "Missing runner command for ${candidate.cliName}" }
    }

    override fun start(
        onHypothesis: (RecognitionHypothesis) -> Unit,
        onError: (Throwable) -> Unit,
    ): Result<Unit> = runCatching {
        callback = onHypothesis
        pcm = ByteArrayOutputStream()
    }

    override fun acceptPcm16(samples: ShortArray, sampleCount: Int) {
        require(sampleCount in 0..samples.size)
        val bytes = ByteBuffer.allocate(sampleCount * 2).order(ByteOrder.LITTLE_ENDIAN)
        repeat(sampleCount) { bytes.putShort(samples[it]) }
        pcm.write(bytes.array())
    }

    override fun stop() {
        val process = ProcessBuilder(runnerCommand + listOf("--candidate", candidate.cliName))
            .redirectError(ProcessBuilder.Redirect.INHERIT)
            .start()
        process.outputStream.use { it.write(wav(pcm.toByteArray())) }
        val output = process.inputStream.bufferedReader().readLine().orEmpty()
        check(process.waitFor() == 0) { "${candidate.cliName} runner failed" }
        val fields = output.split('\t')
        require(fields.size == 3) { "Runner output must be: text<TAB>confidence<TAB>milliseconds" }
        lastInferenceMillis = fields[2].toLong()
        callback?.invoke(RecognitionHypothesis(fields[0], fields[1].toFloat(), true))
    }

    override fun close() {
        callback = null
        pcm.reset()
    }

    private fun wav(data: ByteArray): ByteArray {
        val out = ByteBuffer.allocate(44 + data.size).order(ByteOrder.LITTLE_ENDIAN)
        out.put("RIFF".toByteArray()).putInt(36 + data.size).put("WAVEfmt ".toByteArray())
        out.putInt(16).putShort(1).putShort(1).putInt(16_000).putInt(32_000)
        out.putShort(2).putShort(16).put("data".toByteArray()).putInt(data.size).put(data)
        return out.array()
    }
}
