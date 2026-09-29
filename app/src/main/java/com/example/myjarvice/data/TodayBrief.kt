package com.example.myjarvice.data

import java.util.Calendar

data class LocalDayWindow(val startsAt: Long, val endsAtExclusive: Long) {
    companion object {
        fun containing(time: Long): LocalDayWindow {
            val start = Calendar.getInstance().apply {
                timeInMillis = time
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            val end = (start.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, 1) }
            return LocalDayWindow(start.timeInMillis, end.timeInMillis)
        }
    }
}

/** A deterministic, phone-only brief. No model or network is needed to open Today. */
data class TodayBrief(
    val savedCount: Int,
    val remindersToday: List<RememberItem>,
    val overdue: List<RememberItem>,
    val nextReminder: RememberItem?,
    val recentItems: List<RememberItem>
) {
    companion object {
        fun from(items: List<RememberItem>, now: Long): TodayBrief {
            val tomorrow = LocalDayWindow.containing(now).endsAtExclusive
            val reminders = items.filter { it.reminderAt != null }.sortedBy { it.reminderAt }
            return TodayBrief(
                savedCount = items.size,
                remindersToday = reminders.filter { it.reminderAt!! >= now && it.reminderAt < tomorrow },
                overdue = reminders.filter { it.reminderAt!! < now },
                nextReminder = reminders.firstOrNull { it.reminderAt!! >= tomorrow },
                recentItems = items.sortedByDescending { it.createdAt }.take(3)
            )
        }
    }
}
