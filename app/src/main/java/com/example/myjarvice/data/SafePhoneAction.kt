package com.example.myjarvice.data

/** A deliberately small allowlist for explicit phone commands. */
data class SafePhoneAction(
    val type: String,
    val query: String,
    val requiresConfirmation: Boolean
)

object SafePhoneActionParser {
    fun parse(input: String): SafePhoneAction? {
        val text = input.trim().replace(Regex("\\s+"), " ")
        if (text.isBlank() || text.length > 240) return null
        val lower = text.lowercase()

        if (lower.matches(Regex("^(what('?s| is) )?(my )?(phone )?(battery|battery level|phone status|wifi status|wi-fi status|connection status)\\??$")) ||
            lower.matches(Regex("^(is my phone )?(charging|online|connected)\\??$"))) {
            return SafePhoneAction("DEVICE_STATUS", "", false)
        }

        Regex("^(turn|switch|enable|disable|toggle) (the )?flashlight( (on|off))?\\??$").matchEntire(lower)?.let { match ->
            val requested = match.groupValues[4].ifBlank {
                when (match.groupValues[1]) { "disable" -> "off"; "enable" -> "on"; else -> "toggle" }
            }
            return SafePhoneAction("FLASHLIGHT", requested, false)
        }

        Regex("^(navigate to|directions to|go to|take me to) (.+)$", RegexOption.IGNORE_CASE).matchEntire(text)?.let { match ->
            val destination = match.groupValues[2].trim().trimEnd('.', '?')
            if (destination.isNotBlank() && destination.length <= 160) return SafePhoneAction("NAVIGATE", destination, false)
        }

        Regex("^(set|create) (an? )?alarm (for|at) (.+)$", RegexOption.IGNORE_CASE).matchEntire(text)?.let { match ->
            return SafePhoneAction("SET_ALARM", match.groupValues[4].trim(), false)
        }

        Regex("^(set|start) (a )?timer for (.+)$", RegexOption.IGNORE_CASE).matchEntire(text)?.let { match ->
            return SafePhoneAction("SET_TIMER", match.groupValues[3].trim(), false)
        }

        Regex("^(open|launch|start) (.+)$", RegexOption.IGNORE_CASE).matchEntire(text)?.let { match ->
            val app = match.groupValues[2].trim().trimEnd('.', '?')
            if (app.isNotBlank() && app.length <= 80) return SafePhoneAction("OPEN_APP", app, false)
        }

        Regex("^(call|dial) (.+)$", RegexOption.IGNORE_CASE).matchEntire(text)?.let { match ->
            return SafePhoneAction("CALL", match.groupValues[2].trim(), true)
        }

        Regex("^(send|message) (a )?whatsapp( message)? (.+)$", RegexOption.IGNORE_CASE).matchEntire(text)?.let { match ->
            return SafePhoneAction("WHATSAPP", match.groupValues[4].trim(), true)
        }

        return null
    }
}
