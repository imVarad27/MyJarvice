package com.example.myjarvice.data

/** Qwen3's documented soft thinking switch keeps small-phone responses bounded. */
object LocalModelResponse {
    fun prompt(modelName: String, text: String): String =
        if (modelName.contains("qwen3", true)) "$text\n/no_think" else text

    fun answer(modelName: String, text: String): String {
        if (!modelName.contains("qwen3", true)) return text.trim()
        // Never show raw reasoning blocks as a final answer, including truncated blocks.
        val withoutComplete = text.replace(Regex("<think>.*?</think>", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE)), "")
        return withoutComplete.substringBefore("<think>").trim()
    }
}
