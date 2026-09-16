package com.example.myjarvice.wake

import org.junit.Assert.*
import org.junit.Test

class WakeActivationPolicyTest {
    @Test fun requiresEnabledProfileAndStrictVoiceMatch() {
        assertTrue(WakeActivationPolicy.permits(true, true, .90f, .82f))
        assertFalse(WakeActivationPolicy.permits(false, true, .95f, .82f))
        assertFalse(WakeActivationPolicy.permits(true, false, .95f, .82f))
        assertFalse(WakeActivationPolicy.permits(true, true, .70f, .82f))
    }
    @Test fun rejectsInvalidScoresAndUnsafeThresholds() {
        assertFalse(WakeActivationPolicy.permits(true, true, Float.NaN, .82f))
        assertFalse(WakeActivationPolicy.permits(true, true, .95f, .50f))
        assertFalse(WakeActivationPolicy.permits(true, true, .95f, Float.NaN))
    }
}
