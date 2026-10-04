package com.example.myjarvice.data

/** Parse bounded tool arguments, not natural-language user commands. */
object ClockActionParameters {
    data class Alarm(val hour: Int, val minute: Int)

    fun alarm(value: String): Alarm {
        val match = Regex("^(\\d{1,2})(?::(\\d{2}))?\\s*(am|pm)?$", RegexOption.IGNORE_CASE)
            .matchEntire(value.trim()) ?: error("Use an explicit alarm time, such as 07:30 or 7:30 PM.")
        val hour = match.groupValues[1].toInt()
        val minute = match.groupValues[2].ifEmpty { "0" }.toInt()
        val meridiem = match.groupValues[3].lowercase()
        require(minute in 0..59) { "Alarm minutes must be between 00 and 59." }
        if (meridiem.isNotEmpty()) {
            require(hour in 1..12) { "Use a valid 12-hour alarm time." }
            return Alarm(hour % 12 + if (meridiem == "pm") 12 else 0, minute)
        }
        require(match.groupValues[2].isNotEmpty() && hour in 0..23) { "Use HH:mm or include AM/PM so the alarm time is unambiguous." }
        return Alarm(hour, minute)
    }

    fun timerSeconds(value: String): Int {
        val match = Regex("^(\\d+(?:\\.\\d+)?)\\s*(seconds?|secs?|s|minutes?|mins?|m|hours?|hrs?|h)$", RegexOption.IGNORE_CASE)
            .matchEntire(value.trim()) ?: error("Include a timer duration and unit, such as 10 minutes.")
        val amount = match.groupValues[1].toDouble()
        val unit = match.groupValues[2].lowercase()
        val seconds = amount * when (unit.first()) { 'h' -> 3600; 'm' -> 60; else -> 1 }
        require(seconds in 1.0..86_400.0 && seconds % 1.0 == 0.0) { "Use whole seconds between one second and 24 hours." }
        return seconds.toInt()
    }
}
