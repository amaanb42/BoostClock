package org.fossify.clock.voice

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class VoiceCommandNormalizerTest {
    @Test
    fun `accepts only complete final commands`() {
        assertEquals(VoiceCommand.SNOOZE, command(" Snooze. "))
        assertEquals(VoiceCommand.STOP, command("STOP"))
        assertNull(command("snooze", final = false))
        assertNull(command("please snooze"))
        assertNull(command("do not stop"))
        assertNull(command("don't snooze"))
        assertNull(command("snooze the alarm"))
        assertNull(command("dismiss"))
    }

    @Test
    fun `rejects a result below the qualified threshold`() {
        assertNull(command("snooze", confidence = 0.89f, threshold = 0.9f))
    }

    private fun command(
        text: String,
        confidence: Float = 1f,
        final: Boolean = true,
        threshold: Float = 0.9f,
    ) = VoiceCommandNormalizer.commandFor(
        RecognitionHypothesis(text, confidence, final),
        threshold,
    )
}
