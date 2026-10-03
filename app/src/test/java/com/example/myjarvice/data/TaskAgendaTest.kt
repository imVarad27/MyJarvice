package com.example.myjarvice.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

class TaskAgendaTest {
    @Test fun `agenda separates overdue today open and completed tasks`() {
        val now = Calendar.getInstance().apply {
            set(2026, Calendar.OCTOBER, 3, 12, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        val start = LocalDayWindow.containing(now).startsAt
        val tasks = listOf(
            LocalTask("overdue", "Overdue", createdAt = 1, dueAt = start - 1),
            LocalTask("today", "Today", createdAt = 2, dueAt = now),
            LocalTask("open", "No date", createdAt = 3),
            LocalTask("done", "Done", createdAt = 4, completedAt = now - 10)
        )

        val agenda = TaskAgenda.from(tasks, now)

        assertEquals(listOf("overdue"), agenda.overdue.map { it.id })
        assertEquals(listOf("today"), agenda.dueToday.map { it.id })
        assertEquals(listOf("overdue", "today", "open"), agenda.open.map { it.id })
        assertEquals(listOf("done"), agenda.completed.map { it.id })
    }

    @Test fun `empty agenda needs no model or network`() {
        val agenda = TaskAgenda.from(emptyList(), 0)
        assertTrue(agenda.open.isEmpty())
        assertTrue(agenda.completed.isEmpty())
    }
}
