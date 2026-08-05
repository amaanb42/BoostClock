package org.fossify.clock.voice

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class EnergyEndpointDetectorTest {
    companion object {
        private const val CHUNK_SIZE = 1_600
    }

    @Test
    fun `steady alarm-like energy does not start an utterance`() {
        val detector = EnergyEndpointDetector()

        repeat(20) {
            assertNull(detector.accept(constantChunk(1_000), CHUNK_SIZE))
        }
    }

    @Test
    fun `speech above noise followed by silence produces one bounded utterance`() {
        val detector = EnergyEndpointDetector()
        repeat(8) { detector.accept(constantChunk(900), CHUNK_SIZE) }
        repeat(4) { detector.accept(constantChunk(4_000), CHUNK_SIZE) }

        var utterance: ShortArray? = null
        repeat(8) {
            utterance = detector.accept(constantChunk(0), CHUNK_SIZE) ?: utterance
        }

        assertNotNull(utterance)
        assertEquals(true, utterance!!.size <= 16_000 * 6)
    }

    @Test
    fun `reset discards a partial utterance`() {
        val detector = EnergyEndpointDetector()
        repeat(8) { detector.accept(constantChunk(0), CHUNK_SIZE) }
        repeat(3) { detector.accept(constantChunk(4_000), CHUNK_SIZE) }

        detector.reset()

        repeat(8) {
            assertNull(detector.accept(constantChunk(0), CHUNK_SIZE))
        }
    }

    private fun constantChunk(value: Int) = ShortArray(CHUNK_SIZE) { value.toShort() }
}
