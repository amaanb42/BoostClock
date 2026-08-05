package org.fossify.clock.voice

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.media.audiofx.AcousticEchoCanceler
import android.media.audiofx.AutomaticGainControl
import android.media.audiofx.NoiseSuppressor
import android.os.Handler
import android.os.HandlerThread
import java.util.concurrent.atomic.AtomicBoolean

/** Activity-owned, transient 16 kHz mono PCM capture. PCM is kept only in bounded memory. */
class AudioRecordVoiceCapture {
    companion object {
        const val SAMPLE_RATE_HZ = 16_000
        private const val READ_CHUNK_SAMPLES = 1_600
        private const val CAPTURE_BUFFER_BYTES = READ_CHUNK_SAMPLES * 4
    }

    private val capturing = AtomicBoolean(false)
    private var recorder: AudioRecord? = null
    private var captureThread: HandlerThread? = null
    private var echoCanceler: AcousticEchoCanceler? = null
    private var noiseSuppressor: NoiseSuppressor? = null
    private var gainControl: AutomaticGainControl? = null

    @SuppressLint("MissingPermission")
    @Suppress("TooGenericExceptionCaught") // Keep device-specific AudioRecord failures off the looper.
    fun start(
        onPcm: (ShortArray, Int) -> Unit,
        onError: (Throwable) -> Unit = {},
    ): Result<Unit> = runCatching {
        check(capturing.compareAndSet(false, true)) { "Voice capture is already running" }
        val minimumBytes = AudioRecord.getMinBufferSize(
            SAMPLE_RATE_HZ,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
        )
        check(minimumBytes > 0) { "No compatible 16 kHz microphone input" }

        val audioRecord = AudioRecord.Builder()
            .setAudioSource(MediaRecorder.AudioSource.VOICE_RECOGNITION)
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(SAMPLE_RATE_HZ)
                    .setChannelMask(AudioFormat.CHANNEL_IN_MONO)
                    .build()
            )
            .setBufferSizeInBytes(maxOf(minimumBytes, CAPTURE_BUFFER_BYTES))
            .build()
        check(audioRecord.state == AudioRecord.STATE_INITIALIZED) { "Microphone initialization failed" }
        recorder = audioRecord
        enableAudioEffects(audioRecord.audioSessionId)

        val thread = HandlerThread("alarm-voice-capture").also { it.start() }
        captureThread = thread
        audioRecord.startRecording()
        check(audioRecord.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
            "Microphone did not start recording"
        }
        Handler(thread.looper).post {
            try {
                val chunk = ShortArray(READ_CHUNK_SAMPLES)
                while (capturing.get()) {
                    val count = audioRecord.read(chunk, 0, chunk.size, AudioRecord.READ_BLOCKING)
                    check(count >= 0) { "Microphone read failed with code $count" }
                    if (count > 0 && capturing.get()) onPcm(chunk, count)
                }
            } catch (error: Throwable) {
                if (capturing.getAndSet(false)) onError(error)
            }
        }
        Unit
    }.onFailure {
        stop()
    }

    fun stop() {
        capturing.set(false)
        runCatching { recorder?.stop() }
        recorder?.release()
        recorder = null
        echoCanceler?.release()
        echoCanceler = null
        noiseSuppressor?.release()
        noiseSuppressor = null
        gainControl?.release()
        gainControl = null
        captureThread?.quitSafely()
        captureThread = null
    }

    private fun enableAudioEffects(audioSessionId: Int) {
        if (AcousticEchoCanceler.isAvailable()) {
            echoCanceler = AcousticEchoCanceler.create(audioSessionId)?.apply { enabled = true }
        }
        if (NoiseSuppressor.isAvailable()) {
            noiseSuppressor = NoiseSuppressor.create(audioSessionId)?.apply { enabled = true }
        }
        if (AutomaticGainControl.isAvailable()) {
            gainControl = AutomaticGainControl.create(audioSessionId)?.apply { enabled = true }
        }
    }
}
