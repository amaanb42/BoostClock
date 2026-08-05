package org.fossify.clock.voice

import android.app.ActivityManager
import android.content.Context
import android.os.Build

enum class VoiceUnsupportedReason {
    ANDROID_VERSION,
    ABI,
    LOW_MEMORY,
    MODEL_UNAVAILABLE,
}

data class VoiceCapability(
    val supported: Boolean,
    val reason: VoiceUnsupportedReason? = null,
) {
    companion object {
        private val SUPPORTED_ABIS = setOf("arm64-v8a", "x86_64")

        fun detect(context: Context): VoiceCapability {
            val activityManager = context.getSystemService(ActivityManager::class.java)
            return evaluate(
                sdkInt = Build.VERSION.SDK_INT,
                supportedAbis = Build.SUPPORTED_ABIS.asList(),
                isLowRamDevice = activityManager?.isLowRamDevice == true,
                hasBundledModel = VoiceModelAvailability.hasBundledModel,
            )
        }

        fun evaluate(
            sdkInt: Int,
            supportedAbis: Collection<String>,
            isLowRamDevice: Boolean,
            hasBundledModel: Boolean = true,
        ): VoiceCapability {
            if (sdkInt < Build.VERSION_CODES.S) {
                return VoiceCapability(false, VoiceUnsupportedReason.ANDROID_VERSION)
            }
            if (supportedAbis.none(SUPPORTED_ABIS::contains)) {
                return VoiceCapability(false, VoiceUnsupportedReason.ABI)
            }
            if (isLowRamDevice) {
                return VoiceCapability(false, VoiceUnsupportedReason.LOW_MEMORY)
            }
            if (!hasBundledModel) {
                return VoiceCapability(false, VoiceUnsupportedReason.MODEL_UNAVAILABLE)
            }
            return VoiceCapability(true)
        }
    }
}

/** The prototype bundle is present; model quality is still informal and unqualified. */
object VoiceModelAvailability {
    const val hasBundledModel = true
}
