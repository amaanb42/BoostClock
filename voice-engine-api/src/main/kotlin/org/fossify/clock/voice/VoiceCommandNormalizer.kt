package org.fossify.clock.voice

import java.text.Normalizer
import java.util.Locale

object VoiceCommandNormalizer {
    private val edgePunctuation = Regex("^[\\p{P}\\p{S}]+|[\\p{P}\\p{S}]+$")
    private val whitespace = Regex("\\s+")

    fun commandFor(hypothesis: RecognitionHypothesis, minimumConfidence: Float): VoiceCommand? {
        if (!hypothesis.isFinal || hypothesis.confidence < minimumConfidence) return null
        val normalized = Normalizer.normalize(hypothesis.text, Normalizer.Form.NFKC)
            .lowercase(Locale.ENGLISH)
            .trim()
            .replace(edgePunctuation, "")
            .replace(whitespace, " ")
        return when (normalized) {
            "snooze" -> VoiceCommand.SNOOZE
            "stop" -> VoiceCommand.STOP
            else -> null
        }
    }
}
