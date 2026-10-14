package com.example.myjarvice.data

import org.junit.Assert.*
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

class TimeRehearsalTest {
    private val zone = TimeZone.getTimeZone("Asia/Kolkata")
    private fun at(day: Int, hour: Int = 0, minute: Int = 0, tz: TimeZone = zone): Long =
        Calendar.getInstance(tz).apply { clear(); set(2026, Calendar.OCTOBER, day, hour, minute) }.timeInMillis
    private fun event(id: Long, from: Long, until: Long, busy: Boolean = true, allDay: Boolean = false) =
        CalendarAgendaItem(id, "Synthetic", from, until, allDay, "", busy)
    private fun onlyToday(until: Long) = CalendarSnapshot(listOf(
        event(1, until, at(14, 18)),
        event(2, at(15, tz = TimeZone.getTimeZone("UTC")), at(21, tz = TimeZone.getTimeZone("UTC")), allDay = true)
    ))
    private fun run(snapshot: CalendarSnapshot, duration: Int = 60, buffer: Int = 10, now: Long = at(14, 8)) =
        TimeRehearsal.evaluate(snapshot, now, duration, buffer, zone)

    @Test fun buffersProtectBothSidesAndOverrunTestsTheSameStart() {
        val result = run(onlyToday(at(14, 11)))
        val slot = result.options.single()
        assertEquals(at(14, 9), slot.protectedFrom)
        assertEquals(at(14, 9, 10), slot.startsAt)
        assertEquals(at(14, 10, 10), slot.endsAt)
        assertEquals(at(14, 10, 20), slot.protectedUntil)
        assertEquals(at(14, 10, 40), slot.overrunEndsAt)
        assertTrue(slot.survivesOverrun)
        assertEquals(90, result.overrunMinutes)
    }

    @Test fun aNominalFitCanFailTheOverrunWithoutMovingTheCommitment() {
        val result = run(onlyToday(at(14, 10, 20)))
        assertEquals(1, result.options.size)
        assertFalse(result.options.single().survivesOverrun)
        assertEquals(0, result.resilientCount)
    }

    @Test fun exactOverrunBoundaryFitsButOneMillisecondLessDoesNot() {
        assertTrue(run(onlyToday(at(14, 10, 50))).options.single().survivesOverrun)
        assertFalse(run(onlyToday(at(14, 10, 50) - 1)).options.single().survivesOverrun)
    }

    @Test fun buffersCanRemoveAnOtherwiseFittingSlot() {
        val snapshot = onlyToday(at(14, 10))
        assertEquals(1, run(snapshot, buffer = 0).options.size)
        assertTrue(run(snapshot, buffer = 10).options.isEmpty())
    }

    @Test fun ninetyMinuteScenarioUses135MinutesNotAnUnsupportedPlannerDuration() {
        val result = run(onlyToday(at(14, 12)), duration = 90, buffer = 20)
        assertEquals(135, result.overrunMinutes)
        assertTrue(result.options.single().survivesOverrun)
    }

    @Test fun incompleteMalformedAndOverLimitSnapshotsAreUnavailableNotAnEmptyFreeWeek() {
        for (snapshot in listOf(CalendarSnapshot(emptyList(), true),
            CalendarSnapshot(listOf(event(1, at(14, 10), at(14, 9)))),
            CalendarSnapshot(List(501) { event(it.toLong(), at(14, 10), at(14, 11)) }))) {
            val result = run(snapshot)
            assertFalse(result.available)
            assertTrue(result.options.isEmpty())
        }
    }

    @Test fun freeEventsDoNotBlockButAllDayBusyDatesDo() {
        val snapshot = onlyToday(at(14, 11))
        val free = event(3, at(14, 9), at(14, 18), busy = false)
        assertEquals(run(snapshot), run(snapshot.copy(events = snapshot.events + free)))
        val utc = TimeZone.getTimeZone("UTC")
        val busy = event(4, at(14, tz = utc), at(15, tz = utc), allDay = true)
        assertTrue(run(snapshot.copy(events = snapshot.events + busy)).options.isEmpty())
    }

    @Test fun samplesAreBoundedToThreePerDayAndAreNotAllPossibleStartingTimes() {
        val meetings = (0..6).flatMap { day -> (0..7).map { gap ->
            event((day * 10 + gap).toLong(), at(14 + day, 9 + gap, 30), at(14 + day, 10 + gap))
        } }
        val result = run(CalendarSnapshot(meetings), duration = 30, buffer = 0)
        assertEquals(21, result.options.size)
        assertEquals(result.options.sortedBy { it.startsAt }, result.options)
        assertTrue(result.options.all { !it.survivesOverrun })
    }

    @Test fun roundingIsLocalAndNoBufferOrCommitmentStartsInThePast() {
        val tz = TimeZone.getTimeZone("Asia/Kathmandu")
        val now = at(14, 9, 1, tz) + 1_000
        val result = TimeRehearsal.evaluate(CalendarSnapshot(emptyList()), now, 30, 10, tz)
        assertEquals(at(14, 9, 15, tz), result.options.first().protectedFrom)
        assertEquals(at(14, 9, 25, tz), result.options.first().startsAt)
        assertTrue(result.options.all { it.protectedFrom >= now })
    }

    @Test fun supportedInputsAreExplicitAndDoNotAcceptUnboundedWork() {
        for ((duration, buffer) in listOf(15 to 10, 240 to 10, 60 to -1, 60 to 999)) {
            try { run(CalendarSnapshot(emptyList()), duration, buffer); fail("Unsupported input") }
            catch (_: IllegalArgumentException) { }
        }
    }

    @Test fun evaluationDoesNotMutateTheSourceOrExistingWeekSuggestions() {
        val snapshot = onlyToday(at(14, 11))
        val before = CalendarWeekPlanner.plan(snapshot, at(14, 8), 60, zone)
        run(snapshot, buffer = 30)
        assertEquals(before, CalendarWeekPlanner.plan(snapshot, at(14, 8), 60, zone))
        assertEquals(listOf(CalendarOpening(at(14, 9), at(14, 10))), before.first().openings)
    }
}
