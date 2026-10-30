package com.example.myjarvice.widget

import com.example.myjarvice.data.LocalDayWindow
import com.example.myjarvice.data.LocalTask
import com.example.myjarvice.data.TaskAgenda
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Calendar

class JarvisWidgetDataTest {

    @Test
    fun `formatWidgetDue formats overdue today tomorrow and future dates`() {
        val calendar = Calendar.getInstance().apply {
            set(2026, Calendar.OCTOBER, 26, 12, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val now = calendar.timeInMillis
        val day = LocalDayWindow.containing(now)

        val noDue = LocalTask("1", "Task 1", createdAt = now)
        assertEquals("", JarvisTodayWidgetProvider.formatWidgetDue(noDue, now))

        val overdue = LocalTask("2", "Overdue task", createdAt = now, dueAt = day.startsAt - 3600_000L)
        assertEquals("Overdue", JarvisTodayWidgetProvider.formatWidgetDue(overdue, now))

        val today = LocalTask("3", "Today task", createdAt = now, dueAt = day.startsAt + 2 * 3600_000L)
        assertEquals("Today", JarvisTodayWidgetProvider.formatWidgetDue(today, now))

        val tomorrow = LocalTask("4", "Tomorrow task", createdAt = now, dueAt = day.endsAtExclusive + 3600_000L)
        assertEquals("Tomorrow", JarvisTodayWidgetProvider.formatWidgetDue(tomorrow, now))

        val recurringToday = LocalTask("5", "Daily task", createdAt = now, dueAt = day.startsAt + 3600_000L, recurrence = com.example.myjarvice.data.TaskRecurrence.DAILY)
        assertEquals("Today · \u21bb", JarvisTodayWidgetProvider.formatWidgetDue(recurringToday, now))

        val recurringNoDue = LocalTask("6", "Habit task", createdAt = now, recurrence = com.example.myjarvice.data.TaskRecurrence.WEEKLY)
        assertEquals("\u21bb Weekly", JarvisTodayWidgetProvider.formatWidgetDue(recurringNoDue, now))
    }

    @Test
    fun `agenda populates top three open tasks for widget display`() {
        val now = System.currentTimeMillis()
        val tasks = (1..6).map { i ->
            LocalTask("t$i", "Task $i", createdAt = (100 - i).toLong(), completedAt = if (i == 1) now else null)
        }

        val agenda = TaskAgenda.from(tasks, now)
        val widgetTasks = agenda.open.take(3)

        assertEquals(3, widgetTasks.size)
        assertEquals(listOf("t2", "t3", "t4"), widgetTasks.map { it.id })
    }

    @Test
    fun `empty agenda provides friendly empty state for widget`() {
        val agenda = TaskAgenda.from(emptyList(), System.currentTimeMillis())
        assertEquals(0, agenda.open.size)
    }
}
