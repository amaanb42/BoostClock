package org.fossify.clock.voice

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VoiceActionGateTest {
    @Test
    fun `only one touch or voice callback can act`() {
        val gate = VoiceActionGate()
        val generation = gate.newGeneration()

        assertTrue(gate.tryClaim(generation))
        assertFalse(gate.tryClaim(generation))
        assertFalse(gate.tryClaimTouch())
    }

    @Test
    fun `callbacks from stale generations are rejected`() {
        val gate = VoiceActionGate()
        val stale = gate.newGeneration()
        gate.invalidate()

        assertFalse(gate.tryClaim(stale))
    }

    @Test
    fun `touch winning race rejects later voice result`() {
        val gate = VoiceActionGate()
        val generation = gate.newGeneration()

        assertTrue(gate.tryClaimTouch())
        assertFalse(gate.tryClaim(generation))
    }
}
