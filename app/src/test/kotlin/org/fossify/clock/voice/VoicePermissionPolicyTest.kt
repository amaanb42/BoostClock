package org.fossify.clock.voice

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VoicePermissionPolicyTest {
    @Test
    fun `granted permission enables voice`() {
        assertEquals(VoicePermissionAction.ENABLE, action(granted = true))
    }

    @Test
    fun `ordinary denial requests permission`() {
        assertEquals(VoicePermissionAction.REQUEST, action(requested = true, rationale = true))
    }

    @Test
    fun `first request asks for permission`() {
        assertEquals(VoicePermissionAction.REQUEST, action())
    }

    @Test
    fun `permanent denial opens application settings`() {
        assertEquals(VoicePermissionAction.OPEN_SETTINGS, action(requested = true))
    }

    @Test
    fun `revocation disables only an enabled preference`() {
        assertTrue(VoicePermissionPolicy.mustDisable(enabled = true, permissionGranted = false))
        assertFalse(VoicePermissionPolicy.mustDisable(enabled = false, permissionGranted = false))
    }

    private fun action(
        granted: Boolean = false,
        requested: Boolean = false,
        rationale: Boolean = false,
    ) = VoicePermissionPolicy.enableAction(granted, requested, rationale)
}
