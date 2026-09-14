package com.example.myjarvice.wake

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VoiceActionPolicyTest {
    @Test fun blocksSensitiveActionFromUnverifiedVoiceSession() {
        assertTrue(VoiceActionPolicy.shouldBlock("call Mom", true, true, false))
        assertTrue(VoiceActionPolicy.shouldBlock("lock my PC", true, true, false))
        assertTrue(VoiceActionPolicy.shouldBlock("remember my passport number", true, true, false))
    }

    @Test fun allowsQuestionsAndVerifiedOrTypedActions() {
        assertFalse(VoiceActionPolicy.shouldBlock("why is the sky blue?", true, true, false))
        assertFalse(VoiceActionPolicy.shouldBlock("call Mom", true, true, true))
        assertFalse(VoiceActionPolicy.shouldBlock("call Mom", false, true, false))
    }
}
