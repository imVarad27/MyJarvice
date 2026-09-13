package com.example.myjarvice.wake

import org.junit.Assert.*
import org.junit.Test

class WakePhraseTest {
    @Test fun acceptsExplicitWakePhrases() {
        listOf("Hey Jarvis", "okay jarvis", "hi jarvis", "OK JARVIS", "hey jarvis help").forEach { assertTrue(it, WakePhrase.matches(it)) }
    }
    @Test fun rejectsNameAloneAndSubstringMatches() {
        listOf("jarvis", "I use Jarvis", "they jarvis", "hey jarvison", "hey", "").forEach { assertFalse(it, WakePhrase.matches(it)) }
    }
}
