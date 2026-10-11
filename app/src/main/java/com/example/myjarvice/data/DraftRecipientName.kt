package com.example.myjarvice.data

/** A bounded, single-line display name, not a contact identifier or permission to send. */
internal object DraftRecipientName {
    fun normalize(raw: String?): String? {
        if (raw == null) return null
        val cleaned = buildString {
            // Bound provider input before normalization as well as the resulting composer field.
            raw.take(4096).forEach { char ->
                when {
                    char.isWhitespace() || Character.isSpaceChar(char) -> append(' ')
                    char.isISOControl() || char in '\u202A'..'\u202E' || char in '\u2066'..'\u2069' -> Unit
                    else -> append(char)
                }
            }
        }.trim().replace(Regex(" +"), " ")
        var end = minOf(cleaned.length, WritingDraftPrompt.MAX_RECIPIENT)
        if (end > 0 && cleaned[end - 1].isHighSurrogate()) end--
        return cleaned.take(end).trim().takeIf { it.isNotBlank() }
    }
}
