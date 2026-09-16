package com.example.myjarvice.wake

object WakePhrase {
    private val phrase = Regex("hey\\s+jarvis[.!?]*", RegexOption.IGNORE_CASE)
    fun matches(text: String) = phrase.matches(text.trim())

    fun confidentWords(words: List<Pair<String, Double>>): Boolean =
        words.map { it.first.lowercase() } == listOf("hey", "jarvis") &&
            words.all { it.second.isFinite() && it.second >= 0.85 }
}
