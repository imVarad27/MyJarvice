package com.example.myjarvice.data

/** UI-only, one-use approval of exact text. Not a model tool, persisted token or send permission. */
internal class ReplyShareApproval private constructor(val text: String) {
    private var used = false

    @Synchronized fun consume(currentText: String): String? {
        if (used) return null
        used = true
        return text.takeIf { it == currentText }
    }

    @Synchronized fun cancel() { used = true }

    companion object {
        const val MAX_CHARS = 20_000

        fun canReview(sender: String, type: String, text: String): Boolean =
            sender != "USER" && type == "RESPONSE" && validText(text)

        fun validText(text: String): Boolean = text.isNotBlank() && text.length <= MAX_CHARS

        fun prepare(text: String): ReplyShareApproval {
            require(validText(text)) { "Review non-empty text within the share limit." }
            return ReplyShareApproval(text)
        }
    }
}
