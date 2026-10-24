package com.example.myjarvice.data

import com.example.myjarvice.ui.main.taskReminderLabel
import org.junit.Assert.*
import org.junit.Test
import java.text.DateFormat
import java.util.Calendar
import java.util.Date

class TaskReminderTest {

    @Test
    fun `task reminder label returns null for completed tasks or tasks without reminder`() {
        val now = 1_000_000L
        val noReminder = LocalTask("1", "Clean desk", createdAt = now)
        val completedWithReminder = LocalTask("2", "Clean desk", createdAt = now, completedAt = now, reminderAt = now + 50_000L)

        assertNull(taskReminderLabel(noReminder, now))
        assertNull(taskReminderLabel(completedWithReminder, now))
    }

    @Test
    fun `task reminder label identifies missed reminders`() {
        val now = 1_000_000L
        val missed = LocalTask("1", "Check mail", createdAt = now - 100_000L, reminderAt = now - 10_000L)

        assertEquals("Reminder missed", taskReminderLabel(missed, now))
    }

    @Test
    fun `task reminder label formats today and tomorrow`() {
        val calendar = Calendar.getInstance().apply {
            set(2026, Calendar.OCTOBER, 24, 10, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val now = calendar.timeInMillis
        val day = LocalDayWindow.containing(now)

        val todayReminderTime = now + 2 * 3600 * 1000L
        val todayTask = LocalTask("1", "Review PR", createdAt = now, reminderAt = todayReminderTime)
        val expectedTimeStr = DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(todayReminderTime))
        assertEquals("Remind today at $expectedTimeStr", taskReminderLabel(todayTask, now))

        val tomorrowReminderTime = day.endsAtExclusive + 9 * 3600 * 1000L
        val tomorrowTask = LocalTask("2", "Standup meeting", createdAt = now, reminderAt = tomorrowReminderTime)
        val expectedTomorrowTimeStr = DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(tomorrowReminderTime))
        assertEquals("Remind tomorrow at $expectedTomorrowTimeStr", taskReminderLabel(tomorrowTask, now))
    }

    @Test
    fun `agenda separates tasks correctly with reminders`() {
        val calendar = Calendar.getInstance().apply {
            set(2026, Calendar.OCTOBER, 24, 12, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val now = calendar.timeInMillis
        val day = LocalDayWindow.containing(now)

        val tasks = listOf(
            LocalTask("t1", "Overdue task", createdAt = 1, dueAt = day.startsAt - 1000L, reminderAt = day.startsAt - 500L),
            LocalTask("t2", "Today task", createdAt = 2, dueAt = now + 1000L, reminderAt = now + 500L),
            LocalTask("t3", "Open task", createdAt = 3, reminderAt = now + 3600_000L),
            LocalTask("t4", "Done task", createdAt = 4, completedAt = now)
        )

        val agenda = TaskAgenda.from(tasks, now)
        assertEquals(listOf("t1"), agenda.overdue.map { it.id })
        assertEquals(listOf("t2"), agenda.dueToday.map { it.id })
        assertEquals(listOf("t1", "t2", "t3"), agenda.open.map { it.id })
        assertEquals(listOf("t4"), agenda.completed.map { it.id })
    }

    @Test
    fun `task defaults provide backward compatibility without reminderAt`() {
        val legacy = LocalTask(
            id = "legacy-1",
            title = "Legacy task",
            notes = "No reminder property",
            createdAt = 123456L
        )

        assertEquals("legacy-1", legacy.id)
        assertNull(legacy.reminderAt)
        assertNull(legacy.dueAt)
        assertFalse(legacy.completed)

        val withReminder = legacy.copy(reminderAt = 999999L)
        assertEquals(999999L, withReminder.reminderAt)
        assertEquals("legacy-1", withReminder.id)
    }
}
