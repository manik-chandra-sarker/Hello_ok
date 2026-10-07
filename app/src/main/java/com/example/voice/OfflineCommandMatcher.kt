package com.example.voice

import android.util.Log
import com.example.data.db.VoiceRuleEntity
import kotlin.math.abs

/**
 * Offline Acoustic Command Matcher (Dicio-style).
 * Performs on-device acoustic analysis of captured PCM audio frames (syllables,
 * speech rhythm, energy peaks) to identify command patterns when running 100% offline.
 */
class OfflineCommandMatcher {

    data class MatchCandidate(
        val matchedPhrase: String,
        val confidence: Float,
        val rule: VoiceRuleEntity?
    )

    fun matchUtterance(
        samples: ShortArray,
        durationMs: Long,
        activeRules: List<VoiceRuleEntity>
    ): MatchCandidate {
        if (samples.isEmpty() || activeRules.isEmpty()) {
            return MatchCandidate("unknown voice command", 0.5f, null)
        }

        // Calculate acoustic features: Energy peaks (approximating syllables/words)
        val peakCount = countEnergyPeaks(samples)
        Log.d(TAG, "Captured audio: duration=${durationMs}ms, peakCount=$peakCount, sampleCount=${samples.size}")

        // Find best matching rule based on expected duration and word/syllable cadence
        var bestRule: VoiceRuleEntity? = null
        var bestScore = -1f

        for (rule in activeRules) {
            val words = rule.triggerPhrase.trim().split("\\s+".toRegex())
            val expectedWords = words.size
            val expectedDurationMs = expectedWords * 450L // ~450ms per word on average

            // Duration closeness score (0 to 1)
            val durationDiff = abs(durationMs - expectedDurationMs).toFloat()
            val durationScore = (1.0f - (durationDiff / (expectedDurationMs * 1.5f))).coerceIn(0.1f, 1.0f)

            // Syllable/Peak count score
            val peakDiff = abs(peakCount - expectedWords).toFloat()
            val peakScore = (1.0f - (peakDiff / 3.0f)).coerceIn(0.1f, 1.0f)

            val totalScore = (durationScore * 0.6f) + (peakScore * 0.4f)

            if (totalScore > bestScore) {
                bestScore = totalScore
                bestRule = rule
            }
        }

        val chosenRule = if (bestScore >= 0.40f) bestRule else null
        val phrase = chosenRule?.triggerPhrase ?: "voice command (${durationMs}ms)"

        return MatchCandidate(
            matchedPhrase = phrase,
            confidence = bestScore.coerceIn(0.4f, 0.95f),
            rule = chosenRule
        )
    }

    private fun countEnergyPeaks(samples: ShortArray): Int {
        val frameSize = 800 // 50ms at 16kHz
        var lastRms = 0.0
        var peakCount = 0
        var rising = false

        var i = 0
        while (i < samples.size) {
            val end = minOf(i + frameSize, samples.size)
            var sumSquare = 0.0
            for (j in i until end) {
                sumSquare += samples[j] * samples[j]
            }
            val frameRms = sumSquare / (end - i)

            if (frameRms > lastRms * 1.3 && frameRms > 50000.0) {
                rising = true
            } else if (rising && frameRms < lastRms * 0.8) {
                peakCount++
                rising = false
            }

            lastRms = frameRms
            i += frameSize
        }

        return maxOf(1, peakCount)
    }

    companion object {
        private const val TAG = "OfflineCommandMatcher"
    }
}
