package com.example.myjarvice.wake

import org.junit.Assert.*
import org.junit.Test
import kotlin.math.PI
import kotlin.math.sin

class EnrollmentAudioTest {
    private fun speech(milliseconds: Int, amplitude: Int = 1200) = ShortArray(milliseconds * 16) {
        (amplitude * sin(2 * PI * 180 * it / 16_000)).toInt().toShort()
    }
    private fun silence(milliseconds: Int) = ShortArray(milliseconds * 16)
    private fun feed(endpoint: EnrollmentEndpoint, audio: ShortArray): Boolean =
        audio.asList().chunked(320).any { frame -> endpoint.accept(frame.toShortArray(), frame.size) }

    @Test fun silenceAndBriefTapDoNotBecomeVoiceSamples() {
        assertEquals(EnrollmentAudio.Issue.TOO_QUIET, EnrollmentAudio.issue(silence(6000)))
        assertEquals(EnrollmentAudio.Issue.TOO_SHORT, EnrollmentAudio.issue(silence(1000) + speech(80) + silence(1000)))
    }

    @Test fun delayedQuietSpeechIsNotPenalizedForTheWaitingTime() {
        val quietSpeech = speech(900, amplitude = 500)
        val delayed = silence(3500) + quietSpeech + silence(1200)
        assertNull(EnrollmentAudio.issue(delayed))
        assertFalse(VoiceprintMatcher.isUsableVoiceSample(delayed))
        assertTrue(VoiceprintMatcher.isUsableVoiceSample(VoiceprintMatcher.trimSilence(delayed)))
    }

    @Test fun ClippingGetsDistinctFeedback() {
        assertEquals(EnrollmentAudio.Issue.CLIPPED,
            EnrollmentAudio.issue(silence(500) + ShortArray(16000) { 32760 } + silence(500)))
    }

    @Test fun waitsForSpeechAndAllowsAPauseBetweenWords() {
        val endpoint = EnrollmentEndpoint()
        assertFalse(feed(endpoint, silence(3500)))
        assertFalse(feed(endpoint, speech(450)))
        assertFalse(feed(endpoint, silence(600)))
        assertFalse(feed(endpoint, speech(500)))
        assertFalse(feed(endpoint, silence(880)))
        assertTrue(feed(endpoint, silence(20)))
    }

    @Test fun briefNoiseDoesNotEndRecordingEarly() {
        val endpoint = EnrollmentEndpoint()
        assertFalse(feed(endpoint, speech(80)))
        assertFalse(feed(endpoint, silence(2000)))
        assertFalse(feed(endpoint, speech(1000)))
        assertTrue(feed(endpoint, silence(900)))
    }
}
