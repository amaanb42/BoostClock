package org.fossify.clock.voice

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Process-scoped model owner. AlarmService may warm the model; only AlarmActivity calls [startListening].
 */
object VoiceRecognitionManager {
    private const val QUALIFIED_CONFIDENCE_THRESHOLD = 1.0f

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val mutableState = MutableStateFlow(RecognitionState.DISABLED)
    val state: StateFlow<RecognitionState> = mutableState.asStateFlow()

    private var engine: VoiceRecognitionEngine? = null
    private var session: VoiceRecognitionSession? = null
    private var preparing = false
    private var preparationGeneration = 0L
    private var listeningGeneration = 0L
    private var capture: AudioRecordVoiceCapture? = null

    @Synchronized
    fun prepare(context: Context) {
        if (preparing || engine != null || !VoiceModelAvailability.hasBundledModel) {
            if (!VoiceModelAvailability.hasBundledModel) {
                mutableState.value = RecognitionState.UNAVAILABLE
            }
            return
        }
        preparing = true
        val callbackGeneration = ++preparationGeneration
        mutableState.value = RecognitionState.LOADING
        val appContext = context.applicationContext
        scope.launch {
            val candidate = BundledVoiceRecognitionEngine.create(appContext)
            val result = candidate.prepare()
            synchronized(this@VoiceRecognitionManager) {
                if (callbackGeneration != preparationGeneration) {
                    candidate.close()
                    return@synchronized
                }
                preparing = false
                if (result.isSuccess) {
                    engine = candidate
                    mutableState.value = RecognitionState.READY
                } else {
                    candidate.close()
                    mutableState.value = RecognitionState.UNAVAILABLE
                }
            }
        }
    }

    @Synchronized
    fun startListening(onCommand: (VoiceCommand) -> Unit): Result<Unit> {
        val activeEngine = engine ?: return Result.failure(IllegalStateException("Voice model is not ready"))
        if (capture != null) return Result.success(Unit)
        val callbackGeneration = ++listeningGeneration

        val newSession = VoiceRecognitionSession(
            engine = activeEngine,
            minimumConfidence = QUALIFIED_CONFIDENCE_THRESHOLD,
            onCommand = { command ->
                mutableState.value = RecognitionState.RECOGNIZED
                onCommand(command)
            },
            onError = { failListening(callbackGeneration) },
        )
        val engineStart = newSession.start()
        if (engineStart.isFailure) {
            mutableState.value = RecognitionState.UNAVAILABLE
            return engineStart
        }

        val newCapture = AudioRecordVoiceCapture()
        val captureStart = newCapture.start(
            onPcm = newSession::acceptPcm16,
            onError = { failListening(callbackGeneration) },
        )
        if (captureStart.isFailure) {
            newSession.stop()
            mutableState.value = RecognitionState.UNAVAILABLE
            return captureStart
        }
        session = newSession
        capture = newCapture
        mutableState.value = RecognitionState.LISTENING
        return Result.success(Unit)
    }

    @Synchronized
    fun stopListening(unavailable: Boolean = false) {
        listeningGeneration += 1
        capture?.stop()
        capture = null
        session?.stop()
        session = null
        mutableState.value = when {
            unavailable -> RecognitionState.UNAVAILABLE
            engine != null -> RecognitionState.READY
            preparing -> RecognitionState.LOADING
            else -> RecognitionState.DISABLED
        }
    }

    @Synchronized
    fun shutdown() {
        stopListening()
        engine?.close()
        engine = null
        preparing = false
        preparationGeneration += 1
        mutableState.value = RecognitionState.DISABLED
    }

    @Synchronized
    private fun failListening(callbackGeneration: Long) {
        if (callbackGeneration == listeningGeneration) {
            stopListening(unavailable = true)
        }
    }
}

private object BundledVoiceRecognitionEngine {
    fun create(context: Context): VoiceRecognitionEngine = MoonshineVoiceRecognitionEngine(context)
}
