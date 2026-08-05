package org.fossify.clock.voice

sealed interface VoiceAlarmAction {
    data object Dismiss : VoiceAlarmAction
    data class Snooze(val durationMinutes: Int) : VoiceAlarmAction

    companion object {
        /** Voice snooze always uses the saved duration, regardless of picker preferences. */
        fun forCommand(command: VoiceCommand, savedSnoozeMinutes: Int): VoiceAlarmAction =
            when (command) {
                VoiceCommand.STOP -> Dismiss
                VoiceCommand.SNOOZE -> Snooze(savedSnoozeMinutes)
            }
    }
}
