package com.example.myjarvice.wake

/** Extra guardrails for commands spoken from an unverified voice session. */
object VoiceActionPolicy {
    fun isSensitive(command: String): Boolean {
        val text = command.trim().lowercase()
        if (text.isBlank()) return false
        return Regex("^(call|dial)\\b").containsMatchIn(text) ||
            Regex("^(send|email|mail|message)\\b").containsMatchIn(text) ||
            Regex("^(remember|forget|delete|remove)\\b").containsMatchIn(text) ||
            Regex("^(remind me|schedule)\\b").containsMatchIn(text) ||
            Regex("^(add|create|complete|finish|cancel|delete|remove)\\s+(a\\s+|my\\s+)?(task|reminder|event)\\b").containsMatchIn(text) ||
            Regex("\\b(lock|shutdown|shut down|restart|delete|remove)\\b.*\\b(pc|computer|laptop|file|folder)\\b").containsMatchIn(text)
    }

    fun shouldBlock(
        command: String,
        voiceMode: Boolean,
        profileEnabled: Boolean,
        ownerVerified: Boolean
    ): Boolean = voiceMode && profileEnabled && !ownerVerified && isSensitive(command)
}
