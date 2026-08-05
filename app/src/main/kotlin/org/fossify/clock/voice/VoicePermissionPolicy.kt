package org.fossify.clock.voice

enum class VoicePermissionAction {
    ENABLE,
    REQUEST,
    OPEN_SETTINGS,
}

object VoicePermissionPolicy {
    fun enableAction(
        permissionGranted: Boolean,
        permissionPreviouslyRequested: Boolean,
        shouldShowRationale: Boolean,
    ): VoicePermissionAction = when {
        permissionGranted -> VoicePermissionAction.ENABLE
        permissionPreviouslyRequested && !shouldShowRationale -> VoicePermissionAction.OPEN_SETTINGS
        else -> VoicePermissionAction.REQUEST
    }

    fun mustDisable(enabled: Boolean, permissionGranted: Boolean) = enabled && !permissionGranted
}
