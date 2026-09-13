package com.example.myjarvice.wake

object WakePhrase {
    private val phrase = Regex("\\b(?:hey|hi|okay|ok)\\s+jarvis\\b", RegexOption.IGNORE_CASE)
    fun matches(text: String) = phrase.containsMatchIn(text)
}
