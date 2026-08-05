package org.fossify.clock.voice

/** Shared by the Android alarm runtime and the non-shipping qualification executable. */
interface VoiceRecognitionEngine {
    suspend fun prepare(): Result<Unit>
    fun start(
        onHypothesis: (RecognitionHypothesis) -> Unit,
        onError: (Throwable) -> Unit = {},
    ): Result<Unit>
    fun acceptPcm16(samples: ShortArray, sampleCount: Int)
    fun stop()
    fun close()
}

enum class RecognitionState {
    DISABLED,
    LOADING,
    READY,
    LISTENING,
    RECOGNIZED,
    UNAVAILABLE,
}

data class RecognitionHypothesis(
    val text: String,
    val confidence: Float,
    val isFinal: Boolean,
)

enum class VoiceCommand {
    SNOOZE,
    STOP,
}
