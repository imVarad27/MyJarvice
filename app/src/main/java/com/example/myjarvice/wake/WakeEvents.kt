package com.example.myjarvice.wake

import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Tiny app-wide signal set when JARVIC is opened by the "Jarvis" wake word, so the
 * chat screen knows to immediately start listening for the spoken command.
 */
object WakeEvents {
    val voiceTrigger = MutableStateFlow(false)
}
