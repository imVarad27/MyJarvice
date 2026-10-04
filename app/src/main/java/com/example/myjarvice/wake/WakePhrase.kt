package com.example.myjarvice.wake

object WakePhrase {
    private const val LIVE_MIN_WORD_CONFIDENCE = 0.55
    private val phrase = Regex("hey\\s+jarvis[.!?]*", RegexOption.IGNORE_CASE)
    fun matches(text: String) = phrase.matches(text.trim())

    /**
     * The always-on recognizer uses a tiny phrase grammar, so its confidence values are
     * substantially lower than the supervised enrollment recognizer. This is only the
     * first gate: a candidate still has to pass the enrolled on-device voice check before
     * Jarvis can open.
     */
    fun confidentWakeCandidate(words: List<Pair<String, Double>>): Boolean =
        words.map { it.first.lowercase() } == listOf("hey", "jarvis") &&
            words.all { it.second.isFinite() && it.second >= LIVE_MIN_WORD_CONFIDENCE }

    fun confidentWords(words: List<Pair<String, Double>>): Boolean =
        words.map { it.first.lowercase() } == listOf("hey", "jarvis") &&
            words.all { it.second.isFinite() && it.second >= 0.85 }
}
