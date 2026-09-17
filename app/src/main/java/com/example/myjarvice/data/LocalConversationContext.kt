package com.example.myjarvice.data

/** Bounded, phone-only conversation context; never sends saved data to the PC. */
object LocalConversationContext {
    private val followUp = Regex("^(tell me more|explain (that|it)|continue|what about|why (is that|so)|and (what|how)|summarize (that|it))\\b", RegexOption.IGNORE_CASE)

    fun retrievalQuery(query: String, history: List<JarvisMessage>): String {
        if (!followUp.containsMatchIn(query.trim())) return query.take(600)
        val previous = history.lastOrNull {
            it.sender.equals("USER", true) && it.type != "ERROR" && it.text.trim() != query.trim()
        }?.text.orEmpty()
        return "${previous.take(300)} ${query.take(300)}".trim()
    }

    fun history(messages: List<JarvisMessage>): String {
        var remaining = 1800
        val lines = messages.filter { it.type != "ERROR" }.takeLast(6).asReversed().mapNotNull {
            if (remaining < 20) return@mapNotNull null
            val role = if (it.sender.equals("USER", true)) "User" else "JARVIS"
            val line = "$role: ${it.text.take(minOf(420, remaining - role.length - 3))}"
            remaining -= line.length + 1
            line
        }
        return lines.asReversed().joinToString("\n")
    }
}
