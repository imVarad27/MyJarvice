package com.example.myjarvice.wake

import org.junit.Assert.*
import org.junit.Test

class WakePhraseTest {
    @Test fun acceptsExplicitWakePhrases() {
        listOf("Hey Jarvis", "HEY JARVIS", "  hey jarvis!  ").forEach { assertTrue(it, WakePhrase.matches(it)) }
    }
    @Test fun rejectsNameAloneAndSubstringMatches() {
        listOf("jarvis", "I use Jarvis", "they jarvis", "hey jarvison", "hey", "", "hi jarvis", "okay jarvis", "hey jarvis help", "say hey jarvis").forEach { assertFalse(it, WakePhrase.matches(it)) }
    }
    @Test fun rejectsUncertainOrIncompleteRecognition() {
        assertTrue(WakePhrase.confidentWords(listOf("hey" to .98, "jarvis" to .92)))
        assertFalse(WakePhrase.confidentWords(listOf("hey" to .98, "jarvis" to .60)))
        assertFalse(WakePhrase.confidentWords(emptyList()))
    }
}
