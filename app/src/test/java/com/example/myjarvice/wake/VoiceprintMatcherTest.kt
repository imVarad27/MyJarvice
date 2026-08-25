package com.example.myjarvice.wake

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.sin
import kotlin.math.sqrt

class VoiceprintMatcherTest {

    private fun generateSyntheticVoice(freq: Double, durationSeconds: Float = 1.0f): ShortArray {
        val sampleRate = 16000
        val totalSamples = (sampleRate * durationSeconds).toInt()
        val samples = ShortArray(totalSamples)
        for (i in 0 until totalSamples) {
            val t = i.toDouble() / sampleRate
            // Harmonic tone mimicking human formant structure (fundamental + 2nd & 3rd harmonics)
            val signal = 0.6 * sin(2.0 * PI * freq * t) +
                    0.3 * sin(2.0 * PI * (freq * 2.0) * t) +
                    0.1 * sin(2.0 * PI * (freq * 3.0) * t)
            samples[i] = (signal * 16000).toInt().toShort()
        }
        return samples
    }

    @Test
    fun computeEmbedding_producesNormalized192DimVector() {
        val voice = generateSyntheticVoice(220.0, 1.5f)
        val embedding = VoiceprintMatcher.computeEmbedding(voice)

        assertEquals(VoiceprintMatcher.EMBEDDING_DIM, embedding.size)

        var normSq = 0.0f
        for (v in embedding) normSq += v * v
        val norm = sqrt(normSq)

        // L2 norm must be approximately 1.0
        assertTrue("Embedding L2 norm should be ~1.0, was $norm", norm in 0.98f..1.02f)
    }

    @Test
    fun identicalAudio_hasPerfectSimilarity() {
        val voice = generateSyntheticVoice(180.0, 2.0f)
        val emb1 = VoiceprintMatcher.computeEmbedding(voice)
        val emb2 = VoiceprintMatcher.computeEmbedding(voice)

        val similarity = VoiceprintMatcher.calculateSimilarity(emb1, emb2)
        assertTrue("Identical audio similarity should be ~1.0, was $similarity", similarity > 0.999f)
    }

    @Test
    fun distinctAudio_hasLowerSimilarityThanSameVoice() {
        val voice1a = generateSyntheticVoice(150.0, 2.0f)
        val voice1b = generateSyntheticVoice(155.0, 2.0f) // Same speaker variation
        val voice2 = generateSyntheticVoice(440.0, 2.0f)  // High pitch different speaker

        val emb1a = VoiceprintMatcher.computeEmbedding(voice1a)
        val emb1b = VoiceprintMatcher.computeEmbedding(voice1b)
        val emb2 = VoiceprintMatcher.computeEmbedding(voice2)

        val sameSpeakerSim = VoiceprintMatcher.calculateSimilarity(emb1a, emb1b)
        val differentSpeakerSim = VoiceprintMatcher.calculateSimilarity(emb1a, emb2)

        assertTrue(
            "Same speaker similarity ($sameSpeakerSim) should exceed different speaker similarity ($differentSpeakerSim)",
            sameSpeakerSim > differentSpeakerSim
        )
    }

    @Test
    fun enrollMasterProfile_aggregatesAndNormalizes() {
        val sample1 = generateSyntheticVoice(200.0, 1.5f)
        val sample2 = generateSyntheticVoice(205.0, 1.5f)
        val sample3 = generateSyntheticVoice(195.0, 1.5f)

        val masterProfile = VoiceprintMatcher.enrollMasterProfile(listOf(sample1, sample2, sample3))

        assertEquals(VoiceprintMatcher.EMBEDDING_DIM, masterProfile.size)

        var normSq = 0.0f
        for (v in masterProfile) normSq += v * v
        val norm = sqrt(normSq)

        assertTrue("Master profile L2 norm should be ~1.0, was $norm", norm in 0.98f..1.02f)

        // Verify that original speaker samples match the master profile
        val (isMatch, score) = VoiceprintMatcher.verify(masterProfile, sample1, threshold = 0.70f)
        assertTrue("Enrolled speaker should match master profile (score was $score)", isMatch)
    }

    @Test
    fun verify_rejectsDissimilarSpeaker() {
        val ownerSample = generateSyntheticVoice(140.0, 2.0f)
        val imposterSample = generateSyntheticVoice(600.0, 2.0f)

        val masterProfile = VoiceprintMatcher.enrollMasterProfile(listOf(ownerSample))

        val (isOwnerMatch, ownerScore) = VoiceprintMatcher.verify(masterProfile, ownerSample, threshold = 0.72f)
        val (isImposterMatch, imposterScore) = VoiceprintMatcher.verify(masterProfile, imposterSample, threshold = 0.72f)

        println("DEBUG: ownerScore=$ownerScore, imposterScore=$imposterScore")

        assertTrue("Owner must be authorized (score was $ownerScore)", isOwnerMatch)
        assertFalse("Imposter must be rejected (score was $imposterScore)", isImposterMatch)
    }
}

