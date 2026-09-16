package com.example.myjarvice.wake

/** Fail closed: no automatic popup without both enrollment and a voice match. */
object WakeActivationPolicy {
    fun permits(profileValid: Boolean, protectionEnabled: Boolean, score: Float, threshold: Float): Boolean =
        profileValid && protectionEnabled && score.isFinite() && threshold.isFinite() &&
            threshold in 0.78f..0.90f && score >= threshold
}
