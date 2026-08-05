package org.fossify.clock.voice

import android.os.Build
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VoiceCapabilityTest {
    @Test
    fun `accepts Android 12 arm64 non-low-ram device with model`() {
        assertTrue(capability().supported)
    }

    @Test
    fun `accepts x86_64 emulator ABI bundled by the prototype`() {
        assertTrue(capability(abis = listOf("x86_64", "arm64-v8a")).supported)
    }

    @Test
    fun `rejects old Android`() {
        assertEquals(VoiceUnsupportedReason.ANDROID_VERSION, capability(sdk = 30).reason)
    }

    @Test
    fun `rejects device without a bundled 64-bit runtime`() {
        assertEquals(VoiceUnsupportedReason.ABI, capability(abis = listOf("armeabi-v7a")).reason)
    }

    @Test
    fun `rejects low-ram device`() {
        assertEquals(VoiceUnsupportedReason.LOW_MEMORY, capability(lowRam = true).reason)
    }

    @Test
    fun `rejects missing qualified bundle`() {
        assertEquals(VoiceUnsupportedReason.MODEL_UNAVAILABLE, capability(model = false).reason)
    }

    private fun capability(
        sdk: Int = Build.VERSION_CODES.S,
        abis: List<String> = listOf("arm64-v8a"),
        lowRam: Boolean = false,
        model: Boolean = true,
    ) = VoiceCapability.evaluate(sdk, abis, lowRam, model)
}
