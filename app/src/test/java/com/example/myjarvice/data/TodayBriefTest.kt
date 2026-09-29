package com.example.myjarvice.data

import org.junit.Assert.*
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

class TodayBriefTest {
    private fun note(id: String, reminder: Long? = null, created: Long = 0) = RememberItem(
        id, RememberKind.TEXT, id, "note", "note", created, reminderAt = reminder
    )

    @Test fun `brief sorts reminders and separates overdue today and tomorrow`() {
        val now = Calendar.getInstance().apply { set(2026, Calendar.SEPTEMBER, 28, 12, 0, 0); set(Calendar.MILLISECOND, 0) }.timeInMillis
        val tomorrow = Calendar.getInstance().apply { timeInMillis = now; add(Calendar.DAY_OF_YEAR, 1); set(Calendar.HOUR_OF_DAY, 0) }.timeInMillis
        val brief = TodayBrief.from(listOf(note("later", now + 2000), note("old", now - 1),
            note("now", now), note("tomorrow", tomorrow), note("no reminder")), now)
        assertEquals(listOf("old"), brief.overdue.map { it.id })
        assertEquals(listOf("now", "later"), brief.remindersToday.map { it.id })
        assertEquals("tomorrow", brief.nextReminder?.id)
        assertEquals(5, brief.savedCount)
    }

    @Test fun `brief uses local midnight across daylight saving changes`() {
        val previous = TimeZone.getDefault()
        try {
            TimeZone.setDefault(TimeZone.getTimeZone("America/New_York"))
            val now = Calendar.getInstance().apply { set(2026, Calendar.MARCH, 8, 0, 0, 0); set(Calendar.MILLISECOND, 0) }.timeInMillis
            val tomorrow = Calendar.getInstance().apply { timeInMillis = now; add(Calendar.DAY_OF_YEAR, 1) }.timeInMillis
            assertEquals(23 * 60 * 60 * 1000L, tomorrow - now)
            val window = LocalDayWindow.containing(now + 12 * 60 * 60 * 1000L)
            assertEquals(now, window.startsAt)
            assertEquals(tomorrow, window.endsAtExclusive)
            val brief = TodayBrief.from(listOf(note("today", tomorrow - 1), note("tomorrow", tomorrow)), now)
            assertEquals(listOf("today"), brief.remindersToday.map { it.id })
            assertEquals("tomorrow", brief.nextReminder?.id)
        } finally { TimeZone.setDefault(previous) }
    }

    @Test fun `recent items are bounded and empty inbox needs no model`() {
        val brief = TodayBrief.from((1..8).map { note("$it", created = it.toLong()) }, 100)
        assertEquals(listOf("8", "7", "6"), brief.recentItems.map { it.id })
        val empty = TodayBrief.from(emptyList(), 100)
        assertTrue(empty.remindersToday.isEmpty())
        assertTrue(empty.overdue.isEmpty())
        assertNull(empty.nextReminder)
        assertEquals(0, empty.savedCount)
    }
}
