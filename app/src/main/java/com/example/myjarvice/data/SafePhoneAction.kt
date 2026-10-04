package com.example.myjarvice.data

/** A validated phone action selected by a model tool call, never by phrase matching. */
data class SafePhoneAction(
    val type: String,
    val query: String,
    val requiresConfirmation: Boolean
)

/** Safety and bounds stay deterministic even though intent selection is model-driven. */
object PhoneActionPolicy {
    private val noArgument = setOf("DEVICE_STATUS", "SHOW_LOCAL_TASKS")
    private val boundedArguments = mapOf(
        "FLASHLIGHT" to 6,
        "OPEN_APP" to 80,
        "NAVIGATE" to 160,
        "SET_ALARM" to 80,
        "SET_TIMER" to 80,
        "ADD_LOCAL_TASK" to 180,
        "CALL" to 120,
        "WHATSAPP" to 500
    )
    private val confirmationRequired = setOf("CALL", "WHATSAPP")

    fun validate(type: String, query: String): SafePhoneAction? {
        val normalizedType = type.trim().uppercase()
        val normalizedQuery = query.trim()
        if (normalizedType in noArgument) {
            return normalizedQuery.takeIf { it.isEmpty() }?.let {
                SafePhoneAction(normalizedType, "", normalizedType in confirmationRequired)
            }
        }
        val maxLength = boundedArguments[normalizedType] ?: return null
        if (normalizedQuery.isBlank() || normalizedQuery.length > maxLength || '\u0000' in normalizedQuery) return null
        if (normalizedType == "FLASHLIGHT" && normalizedQuery.lowercase() !in setOf("on", "off", "toggle")) return null
        if (normalizedType == "SET_ALARM" && runCatching { ClockActionParameters.alarm(normalizedQuery) }.isFailure) return null
        if (normalizedType == "SET_TIMER" && runCatching { ClockActionParameters.timerSeconds(normalizedQuery) }.isFailure) return null
        return SafePhoneAction(normalizedType, normalizedQuery, normalizedType in confirmationRequired)
    }
}
