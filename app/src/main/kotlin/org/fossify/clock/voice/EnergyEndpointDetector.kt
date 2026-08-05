package org.fossify.clock.voice

import kotlin.math.sqrt

/** Small prototype endpoint detector; it adapts its speech threshold to steady alarm playback. */
class EnergyEndpointDetector(
    private val sampleRate: Int = AudioRecordVoiceCapture.SAMPLE_RATE_HZ,
) {
    companion object {
        private const val WARMUP_MILLIS = 500
        private const val PRE_ROLL_MILLIS = 300
        private const val END_SILENCE_MILLIS = 650
        private const val MAX_UTTERANCE_MILLIS = 6_000
        private const val MIN_RMS = 550.0
        private const val START_NOISE_MULTIPLIER = 1.8
        private const val END_THRESHOLD_MULTIPLIER = 0.72
        private const val NOISE_SMOOTHING = 0.08
        private const val REQUIRED_LOUD_CHUNKS = 2
    }

    private val warmupSamples = samplesFor(WARMUP_MILLIS)
    private val preRollSamples = samplesFor(PRE_ROLL_MILLIS)
    private val endSilenceSamples = samplesFor(END_SILENCE_MILLIS)
    private val maxUtteranceSamples = samplesFor(MAX_UTTERANCE_MILLIS)
    private val preRoll = ArrayDeque<Short>()
    private var utterance = ShortArray(maxUtteranceSamples)
    private var utteranceSize = 0
    private var observedSamples = 0
    private var silentSamples = 0
    private var loudChunks = 0
    private var noiseRms = MIN_RMS
    private var speaking = false

    fun accept(samples: ShortArray, sampleCount: Int): ShortArray? {
        require(sampleCount in 0..samples.size)
        if (sampleCount == 0) return null
        val rms = rms(samples, sampleCount)
        val startThreshold = maxOf(MIN_RMS, noiseRms * START_NOISE_MULTIPLIER)
        observedSamples += sampleCount

        if (!speaking) {
            appendPreRoll(samples, sampleCount)
            if (observedSamples < warmupSamples) {
                updateNoise(rms)
                return null
            }
            if (rms >= startThreshold) {
                loudChunks += 1
                if (loudChunks >= REQUIRED_LOUD_CHUNKS) beginUtterance()
            } else {
                loudChunks = 0
                updateNoise(rms)
            }
            return null
        }

        appendUtterance(samples, sampleCount)
        silentSamples = if (rms < startThreshold * END_THRESHOLD_MULTIPLIER) {
            silentSamples + sampleCount
        } else {
            0
        }
        return if (silentSamples >= endSilenceSamples || utteranceSize >= maxUtteranceSamples) {
            finishUtterance()
        } else {
            null
        }
    }

    fun reset() {
        preRoll.clear()
        utteranceSize = 0
        observedSamples = 0
        silentSamples = 0
        loudChunks = 0
        noiseRms = MIN_RMS
        speaking = false
    }

    private fun beginUtterance() {
        speaking = true
        utteranceSize = 0
        preRoll.forEach { sample ->
            if (utteranceSize < utterance.size) utterance[utteranceSize++] = sample
        }
        preRoll.clear()
    }

    private fun finishUtterance(): ShortArray {
        val result = utterance.copyOf(utteranceSize)
        speaking = false
        utteranceSize = 0
        silentSamples = 0
        loudChunks = 0
        preRoll.clear()
        return result
    }

    private fun appendPreRoll(samples: ShortArray, sampleCount: Int) {
        repeat(sampleCount) { index ->
            preRoll.addLast(samples[index])
            if (preRoll.size > preRollSamples) preRoll.removeFirst()
        }
    }

    private fun appendUtterance(samples: ShortArray, sampleCount: Int) {
        val writable = minOf(sampleCount, utterance.size - utteranceSize)
        samples.copyInto(utterance, utteranceSize, 0, writable)
        utteranceSize += writable
    }

    private fun updateNoise(rms: Double) {
        noiseRms += (rms - noiseRms) * NOISE_SMOOTHING
    }

    private fun rms(samples: ShortArray, sampleCount: Int): Double {
        var sum = 0.0
        repeat(sampleCount) { index ->
            val value = samples[index].toDouble()
            sum += value * value
        }
        return sqrt(sum / sampleCount)
    }

    private fun samplesFor(milliseconds: Int) = sampleRate * milliseconds / 1_000
}
