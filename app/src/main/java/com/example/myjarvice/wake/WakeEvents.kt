package com.example.myjarvice.wake

import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Tiny app-wide signal set when JARVIC is opened by the "Jarvis" wake word, so the
 * chat screen knows to immediately start listening for the spoken command.
 */
object WakeEvents {
    private val microphoneOwners = mutableSetOf<String>()
    @Synchronized fun setMicrophoneBusy(owner: String, busy: Boolean) {
        if (busy) microphoneOwners.add(owner) else microphoneOwners.remove(owner)
        microphoneBusy.value = microphoneOwners.isNotEmpty()
    }
    val voiceTrigger = MutableStateFlow(false)
    val microphoneBusy = MutableStateFlow(false)
    val captureReleased = MutableStateFlow(true)
    val appVisible = MutableStateFlow(false)
    val popupVisible = MutableStateFlow(false)
    val openSessionId = MutableStateFlow<String?>(null)
    val status = MutableStateFlow("Off")
    val running = MutableStateFlow(false)
    /** True only for the current voice session after an enrolled wake phrase matched locally. */
    val ownerVerified = MutableStateFlow(false)
    /** Last local match score, for a useful status in Settings without uploading audio. */
    val lastVoiceMatchScore = MutableStateFlow<Float?>(null)
}
