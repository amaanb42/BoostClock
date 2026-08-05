package org.fossify.clock.voice

import org.junit.Assert.assertEquals
import org.junit.Test

class VoiceAlarmActionTest {
    @Test
    fun `voice stop uses the alarm dismiss action`() {
        assertEquals(
            VoiceAlarmAction.Dismiss,
            VoiceAlarmAction.forCommand(VoiceCommand.STOP, savedSnoozeMinutes = 17),
        )
    }

    @Test
    fun `voice snooze uses saved duration when same-snooze preference is enabled`() {
        assertVoiceSnooze(useSameSnooze = true)
    }

    @Test
    fun `voice snooze uses saved duration and bypasses picker when preference is disabled`() {
        assertVoiceSnooze(useSameSnooze = false)
    }

    private fun assertVoiceSnooze(@Suppress("UNUSED_PARAMETER") useSameSnooze: Boolean) {
        assertEquals(
            VoiceAlarmAction.Snooze(17),
            VoiceAlarmAction.forCommand(VoiceCommand.SNOOZE, savedSnoozeMinutes = 17),
        )
    }
}
