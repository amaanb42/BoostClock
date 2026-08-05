package org.fossify.clock.voice

/**
 * Runtime-neutral session boundary. It rejects duplicate hypotheses and every callback emitted
 * after stop/restart, so native engines cannot act on a newer alarm by accident.
 */
class VoiceRecognitionSession(
    private val engine: VoiceRecognitionEngine,
    private val minimumConfidence: Float,
    private val onCommand: (VoiceCommand) -> Unit,
    private val onError: (Throwable) -> Unit = {},
) {
    private val lock = Any()
    private var generation = 0L
    private var active = false
    private var commandDelivered = false

    fun start(): Result<Unit> {
        val callbackGeneration = synchronized(lock) {
            generation += 1
            active = true
            commandDelivered = false
            generation
        }
        val result = engine.start(
            onHypothesis = { hypothesis ->
                val command = VoiceCommandNormalizer.commandFor(
                    hypothesis,
                    minimumConfidence,
                )
                if (command != null) {
                    val deliver = synchronized(lock) {
                        if (!active || generation != callbackGeneration || commandDelivered) {
                            false
                        } else {
                            commandDelivered = true
                            true
                        }
                    }
                    if (deliver) onCommand(command)
                }
            },
            onError = { error ->
                val deliver = synchronized(lock) {
                    if (!active || generation != callbackGeneration) {
                        false
                    } else {
                        active = false
                        generation += 1
                        true
                    }
                }
                if (deliver) onError(error)
            },
        )
        if (result.isFailure) {
            synchronized(lock) {
                if (generation == callbackGeneration) active = false
            }
        }
        return result
    }

    fun acceptPcm16(samples: ShortArray, sampleCount: Int) {
        if (synchronized(lock) { active }) {
            engine.acceptPcm16(samples, sampleCount)
        }
    }

    fun stop() {
        synchronized(lock) {
            active = false
            generation += 1
        }
        engine.stop()
    }
}
