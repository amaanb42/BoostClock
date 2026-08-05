package org.fossify.clock.voice

import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

/** Prevents duplicate actions and rejects callbacks from an older alarm/listening session. */
class VoiceActionGate {
    private val generation = AtomicLong(0)
    private val actionTaken = AtomicBoolean(false)

    fun newGeneration(): Long {
        actionTaken.set(false)
        return generation.incrementAndGet()
    }

    fun invalidate() {
        generation.incrementAndGet()
    }

    fun tryClaim(callbackGeneration: Long): Boolean =
        generation.get() == callbackGeneration && actionTaken.compareAndSet(false, true)

    fun tryClaimTouch(): Boolean = actionTaken.compareAndSet(false, true)
}
