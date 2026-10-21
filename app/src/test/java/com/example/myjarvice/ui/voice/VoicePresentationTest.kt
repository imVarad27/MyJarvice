package com.example.myjarvice.ui.voice

import org.junit.Assert.*
import org.junit.Test

class VoicePresentationTest {
    @Test fun idleOffersARealListeningRetryNotAMuteToggle() {
        val state = voicePresentation(false, false, false, false, "")
        assertTrue(state.canRetry)
        assertEquals("Start listening", state.micDescription)
        assertEquals("Ready when you are", state.title)
    }

    @Test fun providerFailuresRemainVisibleAndRetryableWithoutPhraseMatching() {
        val state = voicePresentation(false, false, false, false, "Synthetic provider unavailable")
        assertEquals("Synthetic provider unavailable", state.title)
        assertTrue(state.canRetry)
    }

    @Test fun listeningSpeakingThinkingAndMutedNeverOfferConcurrentCapture() {
        for (flags in listOf(listOf(true, false, false, false), listOf(false, true, false, false),
            listOf(false, false, true, false), listOf(false, false, false, true))) {
            assertFalse(voicePresentation(flags[0], flags[1], flags[2], flags[3], "").canRetry)
        }
        assertEquals("Unmute microphone", voicePresentation(false, false, false, true, "").micDescription)
    }

    @Test fun mutedMicrophoneDoesNotHideAnInProgressReply() {
        val state = voicePresentation(false, false, true, true, "Old provider status")
        assertEquals("Thinking…", state.title)
        assertEquals("Microphone muted", state.detail)
        assertFalse(state.canRetry)
    }

    @Test fun micLevelsAreBoundedAndRejectNonFiniteProviderValues() {
        assertEquals(0f, normalizedVoiceLevel(-100f), 0f)
        assertEquals(1f, normalizedVoiceLevel(100f), 0f)
        assertEquals(0.5f, normalizedVoiceLevel(4f), 0f)
        for (level in listOf(Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY)) {
            assertEquals(0f, normalizedVoiceLevel(level), 0f)
        }
    }
}
