package com.example.myjarvice.data

import java.util.Calendar

enum class TaskRecurrence {
    NONE,
    DAILY,
    WEEKDAYS,
    WEEKLY,
    MONTHLY;

    val label: String get() = when (this) {
        NONE -> "Does not repeat"
        DAILY -> "Repeats daily"
        WEEKDAYS -> "Repeats weekdays"
        WEEKLY -> "Repeats weekly"
        MONTHLY -> "Repeats monthly"
    }

    val chipLabel: String get() = when (this) {
        NONE -> "No repeat"
        DAILY -> "Daily"
        WEEKDAYS -> "Weekdays"
        WEEKLY -> "Weekly"
        MONTHLY -> "Monthly"
    }
}

object TaskRecurrenceCalculator {

    /**
     * Calculates the next occurrence timestamp for a recurring task.
     * Advances by the recurrence period starting from [baseTime] (or [fromTime] if baseTime is null),
     * ensuring the result is strictly greater than [fromTime].
     */
    fun computeNextDue(baseTime: Long?, recurrence: TaskRecurrence, fromTime: Long): Long? {
        if (recurrence == TaskRecurrence.NONE) return null
        val calendar = Calendar.getInstance().apply {
            timeInMillis = baseTime ?: fromTime
        }
        when (recurrence) {
            TaskRecurrence.DAILY -> {
                do {
                    calendar.add(Calendar.DAY_OF_YEAR, 1)
                } while (calendar.timeInMillis <= fromTime)
            }
            TaskRecurrence.WEEKDAYS -> {
                do {
                    calendar.add(Calendar.DAY_OF_YEAR, 1)
                    while (calendar.get(Calendar.DAY_OF_WEEK) == Calendar.SATURDAY ||
                        calendar.get(Calendar.DAY_OF_WEEK) == Calendar.SUNDAY) {
                        calendar.add(Calendar.DAY_OF_YEAR, 1)
                    }
                } while (calendar.timeInMillis <= fromTime)
            }
            TaskRecurrence.WEEKLY -> {
                do {
                    calendar.add(Calendar.WEEK_OF_YEAR, 1)
                } while (calendar.timeInMillis <= fromTime)
            }
            TaskRecurrence.MONTHLY -> {
                do {
                    calendar.add(Calendar.MONTH, 1)
                } while (calendar.timeInMillis <= fromTime)
            }
            TaskRecurrence.NONE -> return null
        }
        return calendar.timeInMillis
    }

    /**
     * Calculates the next reminder timestamp for a recurring task.
     * If the task had both a due date and a reminder, preserves the offset (dueAt - reminderAt).
     * If the task only had a reminder, advances the reminder by the recurrence rule.
     */
    fun computeNextReminder(
        oldDueAt: Long?,
        oldReminderAt: Long?,
        recurrence: TaskRecurrence,
        nextDueAt: Long?,
        fromTime: Long
    ): Long? {
        if (recurrence == TaskRecurrence.NONE || oldReminderAt == null) return null
        return if (oldDueAt != null && nextDueAt != null) {
            val delta = oldDueAt - oldReminderAt
            val nextReminder = nextDueAt - delta
            if (nextReminder > fromTime) nextReminder else null
        } else {
            computeNextDue(oldReminderAt, recurrence, fromTime)
        }
    }
}
