package com.example.myjarvice.wake

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VoiceActionPolicyTest {
    @Test fun blocksSensitiveActionFromUnverifiedVoiceSession() {
        assertTrue(VoiceActionPolicy.shouldBlock(true, true, true, false))
    }

    @Test fun allowsQuestionsAndVerifiedOrTypedActions() {
        assertFalse(VoiceActionPolicy.shouldBlock(false, true, true, false))
        assertFalse(VoiceActionPolicy.shouldBlock(true, true, true, true))
        assertFalse(VoiceActionPolicy.shouldBlock(true, false, true, false))
    }
}
