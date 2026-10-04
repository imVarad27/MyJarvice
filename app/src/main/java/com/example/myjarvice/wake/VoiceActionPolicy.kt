package com.example.myjarvice.wake

/** Voice authorization uses selected capability metadata, never user wording. */
object VoiceActionPolicy {
    fun shouldBlock(
        changesState: Boolean,
        voiceMode: Boolean,
        profileEnabled: Boolean,
        ownerVerified: Boolean
    ): Boolean = changesState && voiceMode && profileEnabled && !ownerVerified
}
