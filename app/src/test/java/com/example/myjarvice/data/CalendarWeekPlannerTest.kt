package com.example.myjarvice.data

import org.junit.Assert.*
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

class CalendarWeekPlannerTest {
    private val zone = TimeZone.getTimeZone("Asia/Kolkata")
    private fun at(day: Int, hour: Int = 0, minute: Int = 0, tz: TimeZone = zone, month: Int = Calendar.OCTOBER): Long =
        Calendar.getInstance(tz).apply { clear(); set(2026, month, day, hour, minute) }.timeInMillis
    private fun event(id: Long, start: Long, end: Long, busy: Boolean = true, allDay: Boolean = false) =
        CalendarAgendaItem(id, "Synthetic $id", start, end, allDay, "", busy)
    private fun plan(events: List<CalendarAgendaItem>, now: Long = at(10, 8), duration: Int = 60) =
        CalendarWeekPlanner.plan(CalendarSnapshot(events), now, duration, zone)

    @Test fun sevenLocalDaysAndOnlyOneSuggestionPerGap() {
        val days = plan(emptyList())
        assertEquals(7, days.size)
        assertEquals(at(10), days.first().window.startsAt)
        assertEquals(at(17), days.last().window.endsAtExclusive)
        assertEquals(listOf(CalendarOpening(at(10, 9), at(10, 10))), days.first().openings)
    }

    @Test fun overlapAndTouchingBoundariesMergeWithoutFalseConflicts() {
        val a = event(1, at(10, 9), at(10, 11))
        val b = event(2, at(10, 10), at(10, 12))
        val c = event(3, at(10, 12), at(10, 13))
        val day = plan(listOf(c, b, a)).first()
        assertEquals(setOf(a, b), day.conflicts)
        assertEquals(listOf(CalendarOpening(at(10, 13), at(10, 14))), day.openings)
    }

    @Test fun freeEventsDoNotBlockOrConflictAndUnknownDefaultsToBusy() {
        val free = event(1, at(10, 9), at(10, 18), busy = false)
        val busy = event(2, at(10, 10), at(10, 11))
        val day = plan(listOf(free, busy)).first()
        assertTrue(day.conflicts.isEmpty())
        assertEquals(listOf(CalendarOpening(at(10, 9), at(10, 10)), CalendarOpening(at(10, 11), at(10, 12))), day.openings)
        assertTrue(CalendarAgendaItem(3, "Unknown", 1, 2, false, "").blocksTime)
    }

    @Test fun allDayUsesUtcDatesForPositiveAndNegativeOffsets() {
        val utc = TimeZone.getTimeZone("UTC")
        val allDay = event(1, at(10, tz = utc), at(12, tz = utc), allDay = true)
        for (tz in listOf(zone, TimeZone.getTimeZone("America/Los_Angeles"), TimeZone.getTimeZone("Pacific/Kiritimati"))) {
            val days = CalendarWeekPlanner.plan(CalendarSnapshot(listOf(allDay)), at(10, 8, tz = tz), 60, tz)
            assertTrue(days[0].openings.isEmpty())
            assertTrue(days[1].openings.isEmpty())
            assertFalse(days[2].openings.isEmpty())
            assertEquals(at(10, tz = tz), CalendarWeekPlanner.interval(allDay, tz).startsAt)
        }
    }

    @Test fun busyAllDayConflictIsConservativeButFreeHolidayIsNotBlocking() {
        val utc = TimeZone.getTimeZone("UTC")
        val allDay = event(1, at(10, tz = utc), at(11, tz = utc), allDay = true)
        val meeting = event(2, at(10, 11), at(10, 12))
        val day = plan(listOf(allDay, meeting)).first()
        assertEquals(setOf(allDay, meeting), day.conflicts)
        assertTrue(day.openings.isEmpty())
        assertFalse(plan(listOf(allDay.copy(blocksTime = false))).first().openings.isEmpty())
    }

    @Test fun overnightInstancesAppearOnBothDaysButDoNotDuplicateAtMidnight() {
        val overnight = event(1, at(10, 17), at(11, 10))
        val endsMidnight = event(2, at(10, 23), at(11))
        val days = plan(listOf(overnight, endsMidnight))
        assertEquals(2, days[0].events.size)
        assertEquals(listOf(overnight), days[1].events)
        assertEquals(listOf(CalendarOpening(at(11, 10), at(11, 11))), days[1].openings)
    }

    @Test fun nowIsRoundedUpLocallyAndPastTimeIsNeverSuggested() {
        val fractionalZone = TimeZone.getTimeZone("Asia/Kathmandu")
        val now = at(10, 9, 1, tz = fractionalZone) + 1_000
        val day = CalendarWeekPlanner.plan(CalendarSnapshot(emptyList()), now, 30, fractionalZone).first()
        assertEquals(at(10, 9, 15, tz = fractionalZone), day.openings.first().startsAt)
        assertEquals(at(10, 9, 45, tz = fractionalZone), day.openings.first().endsAt)
        assertTrue(plan(emptyList(), at(10, 18)).first().openings.isEmpty())
        assertEquals(at(10, 9, 15), plan(emptyList(), at(10, 9, 15)).first().openings.first().startsAt)
    }

    @Test fun minimumDurationAndMaximumThreeSuggestionsAreEnforced() {
        val meetings = listOf(event(1, at(10, 9, 30), at(10, 10)), event(2, at(10, 10, 30), at(10, 11)),
            event(3, at(10, 11, 30), at(10, 12)), event(4, at(10, 12, 30), at(10, 13)))
        assertEquals(3, plan(meetings, duration = 30).first().openings.size)
        assertEquals(listOf(CalendarOpening(at(10, 13), at(10, 14, 30))), plan(meetings, duration = 90).first().openings)
        try { plan(emptyList(), duration = 15); fail("Unsupported duration accepted") } catch (_: IllegalArgumentException) { }
    }

    @Test fun incompleteMalformedAndOverLimitSnapshotsNeverSuggestFreeTime() {
        val now = at(10, 8)
        val incomplete = CalendarWeekPlanner.plan(CalendarSnapshot(emptyList(), true), now, 60, zone)
        assertTrue(incomplete.all { it.openings.isEmpty() })
        assertTrue(plan(listOf(event(1, at(10, 10), at(10, 9)))).all { it.openings.isEmpty() })
        assertTrue(plan((1L..501L).map { event(it, at(10, 10), at(10, 11)) }).all { it.openings.isEmpty() })
    }

    @Test fun duplicateInstancesCollapseButRecurringOccurrencesRemain() {
        val a = event(1, at(10, 9), at(10, 10))
        val b = a.copy(startsAt = at(11, 9), endsAt = at(11, 10))
        val days = plan(listOf(a, a, b))
        assertEquals(listOf(a), days[0].events)
        assertEquals(listOf(b), days[1].events)
        assertTrue(days[0].conflicts.isEmpty())
    }

    @Test fun daylightSavingUsesCalendarDaysNotFixedTwentyFourHourDays() {
        val ny = TimeZone.getTimeZone("America/New_York")
        val now = at(8, 8, tz = ny, month = Calendar.MARCH)
        val days = CalendarWeekPlanner.days(now, ny)
        assertEquals(23 * 60 * 60 * 1000L, days[0].endsAtExclusive - days[0].startsAt)
        val autumn = CalendarWeekPlanner.days(at(1, 8, tz = ny, month = Calendar.NOVEMBER), ny)
        assertEquals(25 * 60 * 60 * 1000L, autumn[0].endsAtExclusive - autumn[0].startsAt)
        val openings = CalendarWeekPlanner.plan(CalendarSnapshot(emptyList()), now, 60, ny).first().openings
        assertEquals(at(8, 9, tz = ny, month = Calendar.MARCH), openings.single().startsAt)
    }
}
