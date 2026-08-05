package org.fossify.clock.voice

import android.content.Context
import com.k2fsa.sherpa.onnx.FeatureConfig
import com.k2fsa.sherpa.onnx.OfflineModelConfig
import com.k2fsa.sherpa.onnx.OfflineMoonshineModelConfig
import com.k2fsa.sherpa.onnx.OfflineRecognizer
import com.k2fsa.sherpa.onnx.OfflineRecognizerConfig
import java.security.MessageDigest
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

class MoonshineVoiceRecognitionEngine(context: Context) : VoiceRecognitionEngine {
    companion object {
        private const val MODEL_DIR =
            "voice/sherpa-onnx-moonshine-tiny-en-quantized-2026-02-27"
        private const val MODEL_CONFIDENCE = 1f
        private const val PCM_16_SCALE = 32_768f
        private val MODEL_HASHES = mapOf(
            "encoder_model.ort" to "94e90a4654fc45cdfedb77c4c08e1739f48862998e58fada384b25118134f221",
            "decoder_model_merged.ort" to
                "cf524c4862d36e9e5ab032eddc73637efd822d70e868ac575cf1a46e1e4708a0",
            "tokens.txt" to "2870d843e14c1e187bf1913a521562a63b53933814bd7f2145120468f494a049",
        )
    }

    private val appContext = context.applicationContext
    private val worker = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "alarm-voice-inference")
    }
    private val inferencePending = AtomicBoolean(false)
    private val lock = Any()
    private val endpointDetector = EnergyEndpointDetector()
    private var recognizer: OfflineRecognizer? = null
    private var callback: ((RecognitionHypothesis) -> Unit)? = null
    private var errorCallback: ((Throwable) -> Unit)? = null
    private var generation = 0L
    private var active = false

    override suspend fun prepare(): Result<Unit> = runCatching {
        verifyAssets()
        val moonshine = OfflineMoonshineModelConfig(
            encoder = "$MODEL_DIR/encoder_model.ort",
            mergedDecoder = "$MODEL_DIR/decoder_model_merged.ort",
        )
        val model = OfflineModelConfig(
            moonshine = moonshine,
            tokens = "$MODEL_DIR/tokens.txt",
            numThreads = 2,
            debug = false,
            provider = "cpu",
        )
        recognizer = OfflineRecognizer(
            assetManager = appContext.assets,
            config = OfflineRecognizerConfig(
                featConfig = FeatureConfig(sampleRate = 16_000, featureDim = 80, dither = 0f),
                modelConfig = model,
            ),
        )
    }

    override fun start(
        onHypothesis: (RecognitionHypothesis) -> Unit,
        onError: (Throwable) -> Unit,
    ): Result<Unit> = runCatching {
        check(recognizer != null) { "Moonshine is not prepared" }
        synchronized(lock) {
            generation += 1
            active = true
            callback = onHypothesis
            errorCallback = onError
            endpointDetector.reset()
        }
    }

    override fun acceptPcm16(samples: ShortArray, sampleCount: Int) {
        val work = synchronized(lock) {
            if (!active) null else endpointDetector.accept(samples, sampleCount)
        } ?: return
        queueInference(work)
    }

    override fun stop() {
        synchronized(lock) {
            generation += 1
            active = false
            callback = null
            errorCallback = null
            endpointDetector.reset()
        }
    }

    override fun close() {
        stop()
        worker.execute {
            recognizer?.release()
            recognizer = null
        }
        worker.shutdown()
    }

    @Suppress("TooGenericExceptionCaught") // JNI may surface both exceptions and linkage errors.
    private fun queueInference(samples: ShortArray) {
        if (!inferencePending.compareAndSet(false, true)) return
        val callbackGeneration = synchronized(lock) { generation }
        worker.execute {
            try {
                val text = recognize(samples)
                val currentCallback = synchronized(lock) {
                    callback.takeIf { active && generation == callbackGeneration }
                }
                if (text.isNotBlank()) {
                    currentCallback?.invoke(
                        RecognitionHypothesis(text = text, confidence = MODEL_CONFIDENCE, isFinal = true)
                    )
                }
            } catch (error: Throwable) {
                val currentErrorCallback = synchronized(lock) {
                    errorCallback.takeIf { active && generation == callbackGeneration }
                }
                currentErrorCallback?.invoke(error)
            } finally {
                inferencePending.set(false)
            }
        }
    }

    private fun recognize(samples: ShortArray): String {
        val currentRecognizer = checkNotNull(recognizer)
        val stream = currentRecognizer.createStream()
        return try {
            val floats = FloatArray(samples.size) { index -> samples[index] / PCM_16_SCALE }
            stream.acceptWaveform(floats, AudioRecordVoiceCapture.SAMPLE_RATE_HZ)
            currentRecognizer.decode(stream)
            currentRecognizer.getResult(stream).text
        } finally {
            stream.release()
        }
    }

    private fun verifyAssets() {
        MODEL_HASHES.forEach { (name, expected) ->
            val actual = hashAsset(name)
            check(actual == expected) { "Voice model checksum mismatch: $name" }
        }
    }

    private fun hashAsset(name: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        appContext.assets.open("$MODEL_DIR/$name").buffered().use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            var count = input.read(buffer)
            while (count >= 0) {
                digest.update(buffer, 0, count)
                count = input.read(buffer)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}
