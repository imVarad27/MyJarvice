package com.example.myjarvice.data

import org.junit.Assert.*
import org.junit.Test
import java.util.Calendar

class TaskRecurrenceTest {

    @Test
    fun `task recurrence labels and chips are user friendly`() {
        assertEquals("Does not repeat", TaskRecurrence.NONE.label)
        assertEquals("No repeat", TaskRecurrence.NONE.chipLabel)

        assertEquals("Repeats daily", TaskRecurrence.DAILY.label)
        assertEquals("Daily", TaskRecurrence.DAILY.chipLabel)

        assertEquals("Repeats weekdays", TaskRecurrence.WEEKDAYS.label)
        assertEquals("Weekdays", TaskRecurrence.WEEKDAYS.chipLabel)

        assertEquals("Repeats weekly", TaskRecurrence.WEEKLY.label)
        assertEquals("Weekly", TaskRecurrence.WEEKLY.chipLabel)

        assertEquals("Repeats monthly", TaskRecurrence.MONTHLY.label)
        assertEquals("Monthly", TaskRecurrence.MONTHLY.chipLabel)
    }

    @Test
    fun `local task repeats property reflects recurrence`() {
        val nonRepeating = LocalTask("1", "Buy groceries", createdAt = 1000L)
        assertFalse(nonRepeating.repeats)
        assertFalse(nonRepeating.completed)

        val daily = LocalTask("2", "Drink water", createdAt = 1000L, recurrence = TaskRecurrence.DAILY)
        assertTrue(daily.repeats)

        val weekdays = LocalTask("3", "Standup meeting", createdAt = 1000L, recurrence = TaskRecurrence.WEEKDAYS)
        assertTrue(weekdays.repeats)
    }

    @Test
    fun `daily recurrence advances one day into the future`() {
        val cal = Calendar.getInstance().apply {
            set(2026, Calendar.OCTOBER, 30, 9, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val baseDue = cal.timeInMillis
        val completedAt = baseDue + 2 * 3600 * 1000L // Completed at 11:00 AM on Oct 30

        val nextDue = TaskRecurrenceCalculator.computeNextDue(baseDue, TaskRecurrence.DAILY, completedAt)
        assertNotNull(nextDue)

        val nextCal = Calendar.getInstance().apply { timeInMillis = nextDue!! }
        assertEquals(2026, nextCal.get(Calendar.YEAR))
        assertEquals(Calendar.OCTOBER, nextCal.get(Calendar.MONTH))
        assertEquals(31, nextCal.get(Calendar.DAY_OF_MONTH))
        assertEquals(9, nextCal.get(Calendar.HOUR_OF_DAY))
        assertEquals(0, nextCal.get(Calendar.MINUTE))
    }

    @Test
    fun `daily recurrence handles overdue tasks advancing past completion time`() {
        val cal = Calendar.getInstance().apply {
            set(2026, Calendar.OCTOBER, 26, 9, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val overdueDue = cal.timeInMillis
        val completedAt = Calendar.getInstance().apply {
            set(2026, Calendar.OCTOBER, 30, 15, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

        val nextDue = TaskRecurrenceCalculator.computeNextDue(overdueDue, TaskRecurrence.DAILY, completedAt)
        assertNotNull(nextDue)

        val nextCal = Calendar.getInstance().apply { timeInMillis = nextDue!! }
        assertEquals(2026, nextCal.get(Calendar.YEAR))
        assertEquals(Calendar.OCTOBER, nextCal.get(Calendar.MONTH))
        assertEquals(31, nextCal.get(Calendar.DAY_OF_MONTH))
        assertEquals(9, nextCal.get(Calendar.HOUR_OF_DAY))
    }

    @Test
    fun `weekdays recurrence skips weekends`() {
        // Friday Oct 30, 2026
        val cal = Calendar.getInstance().apply {
            set(2026, Calendar.OCTOBER, 30, 10, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val fridayDue = cal.timeInMillis
        val completedAt = fridayDue + 3600 * 1000L

        val nextDue = TaskRecurrenceCalculator.computeNextDue(fridayDue, TaskRecurrence.WEEKDAYS, completedAt)
        assertNotNull(nextDue)

        val nextCal = Calendar.getInstance().apply { timeInMillis = nextDue!! }
        // Should advance past Saturday Oct 31 and Sunday Nov 1 to Monday Nov 2
        assertEquals(2026, nextCal.get(Calendar.YEAR))
        assertEquals(Calendar.NOVEMBER, nextCal.get(Calendar.MONTH))
        assertEquals(2, nextCal.get(Calendar.DAY_OF_MONTH))
        assertEquals(Calendar.MONDAY, nextCal.get(Calendar.DAY_OF_WEEK))
        assertEquals(10, nextCal.get(Calendar.HOUR_OF_DAY))
    }

    @Test
    fun `weekdays recurrence advances normally from Monday to Tuesday`() {
        // Monday Nov 2, 2026
        val cal = Calendar.getInstance().apply {
            set(2026, Calendar.NOVEMBER, 2, 10, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val mondayDue = cal.timeInMillis
        val completedAt = mondayDue + 3600 * 1000L

        val nextDue = TaskRecurrenceCalculator.computeNextDue(mondayDue, TaskRecurrence.WEEKDAYS, completedAt)
        assertNotNull(nextDue)

        val nextCal = Calendar.getInstance().apply { timeInMillis = nextDue!! }
        assertEquals(Calendar.TUESDAY, nextCal.get(Calendar.DAY_OF_WEEK))
        assertEquals(3, nextCal.get(Calendar.DAY_OF_MONTH))
    }

    @Test
    fun `weekly recurrence advances by seven days`() {
        val cal = Calendar.getInstance().apply {
            set(2026, Calendar.OCTOBER, 30, 14, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val baseDue = cal.timeInMillis
        val completedAt = baseDue + 1000L

        val nextDue = TaskRecurrenceCalculator.computeNextDue(baseDue, TaskRecurrence.WEEKLY, completedAt)
        assertNotNull(nextDue)

        val nextCal = Calendar.getInstance().apply { timeInMillis = nextDue!! }
        assertEquals(Calendar.NOVEMBER, nextCal.get(Calendar.MONTH))
        assertEquals(6, nextCal.get(Calendar.DAY_OF_MONTH)) // Oct 30 + 7 days = Nov 6
        assertEquals(14, nextCal.get(Calendar.HOUR_OF_DAY))
    }

    @Test
    fun `monthly recurrence advances by one month`() {
        val cal = Calendar.getInstance().apply {
            set(2026, Calendar.OCTOBER, 15, 8, 30, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val baseDue = cal.timeInMillis
        val completedAt = baseDue + 1000L

        val nextDue = TaskRecurrenceCalculator.computeNextDue(baseDue, TaskRecurrence.MONTHLY, completedAt)
        assertNotNull(nextDue)

        val nextCal = Calendar.getInstance().apply { timeInMillis = nextDue!! }
        assertEquals(Calendar.NOVEMBER, nextCal.get(Calendar.MONTH))
        assertEquals(15, nextCal.get(Calendar.DAY_OF_MONTH))
        assertEquals(8, nextCal.get(Calendar.HOUR_OF_DAY))
        assertEquals(30, nextCal.get(Calendar.MINUTE))
    }

    @Test
    fun `none recurrence returns null for next occurrence`() {
        val nextDue = TaskRecurrenceCalculator.computeNextDue(1000L, TaskRecurrence.NONE, 2000L)
        assertNull(nextDue)
    }

    @Test
    fun `next reminder preserves relative offset to due date`() {
        val oldDue = 10_000_000L
        val oldReminder = 9_000_000L // 1,000,000L before due date
        val nextDue = 20_000_000L
        val completedAt = 10_500_000L

        val nextReminder = TaskRecurrenceCalculator.computeNextReminder(
            oldDueAt = oldDue,
            oldReminderAt = oldReminder,
            recurrence = TaskRecurrence.DAILY,
            nextDueAt = nextDue,
            fromTime = completedAt
        )

        assertEquals(19_000_000L, nextReminder)
    }
}
