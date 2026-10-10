package com.example.myjarvice.data

import java.util.Calendar
import java.util.TimeZone

data class CalendarSnapshot(val events: List<CalendarAgendaItem>, val incomplete: Boolean = false)
data class CalendarOpening(val startsAt: Long, val endsAt: Long)
data class CalendarPlanDay(
    val window: LocalDayWindow,
    val events: List<CalendarAgendaItem>,
    val conflicts: Set<CalendarAgendaItem>,
    val openings: List<CalendarOpening>
)

/** Deterministic calendar arithmetic, not AI inference. No event text is persisted or sent anywhere. */
internal object CalendarWeekPlanner {
    const val MAX_EVENTS = 500
    val durations = listOf(30, 60, 90)
    private const val MINUTE_MS = 60_000L

    fun days(now: Long, zone: TimeZone = TimeZone.getDefault()): List<LocalDayWindow> {
        val start = Calendar.getInstance(zone).apply {
            timeInMillis = now
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }
        return List(7) {
            val from = start.timeInMillis
            start.add(Calendar.DAY_OF_YEAR, 1)
            LocalDayWindow(from, start.timeInMillis)
        }
    }

    /** All-day dates are UTC calendar dates, not midnight instants in the phone's time zone. */
    fun interval(event: CalendarAgendaItem, zone: TimeZone): CalendarOpening {
        fun localDate(utcMillis: Long): Long {
            val utc = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply { timeInMillis = utcMillis }
            return Calendar.getInstance(zone).apply {
                clear(); set(utc.get(Calendar.YEAR), utc.get(Calendar.MONTH), utc.get(Calendar.DAY_OF_MONTH))
            }.timeInMillis
        }
        return if (event.allDay) CalendarOpening(localDate(event.startsAt), localDate(event.endsAt))
            else CalendarOpening(event.startsAt, event.endsAt)
    }

    fun plan(snapshot: CalendarSnapshot, now: Long, durationMinutes: Int = 60,
        zone: TimeZone = TimeZone.getDefault()): List<CalendarPlanDay> {
        require(durationMinutes in durations) { "Choose 30, 60 or 90 minutes." }
        val events = snapshot.events.distinctBy { Triple(it.eventId, it.startsAt, it.endsAt) }.take(MAX_EVENTS)
        val intervals = events.associateWith { interval(it, zone) }
        val incomplete = snapshot.incomplete || snapshot.events.size > MAX_EVENTS || intervals.values.any { it.endsAt <= it.startsAt }
        return days(now, zone).map { day ->
            val daily = events.filter {
                val range = intervals.getValue(it)
                range.startsAt < day.endsAtExclusive && range.endsAt > day.startsAt && range.endsAt > range.startsAt
            }.sortedWith(compareByDescending<CalendarAgendaItem> { it.allDay }.thenBy { it.startsAt })
            val busy = daily.filter { it.blocksTime }
            val conflicts = mutableSetOf<CalendarAgendaItem>()
            // Bounded to 500 instances; half-open intervals don't mark adjacent events as conflicts.
            busy.forEachIndexed { index, first ->
                busy.drop(index + 1).forEach { second ->
                    val a = intervals.getValue(first); val b = intervals.getValue(second)
                    if (maxOf(a.startsAt, b.startsAt, day.startsAt) < minOf(a.endsAt, b.endsAt, day.endsAtExclusive)) {
                        conflicts += first; conflicts += second
                    }
                }
            }
            // Fixed daytime window is explicitly described by the UI, not inferred availability.
            fun hour(value: Int): Long = Calendar.getInstance(zone).apply {
                timeInMillis = day.startsAt; set(Calendar.HOUR_OF_DAY, value)
            }.timeInMillis
            val start = maxOf(hour(9), now)
            val end = hour(18)
            val openings = mutableListOf<CalendarOpening>()
            if (!incomplete && start < end) {
                val blocked = busy.map { intervals.getValue(it) }.sortedBy { it.startsAt }
                var cursor = start
                fun offer(until: Long) {
                    // Align to a 15-minute boundary in LOCAL wall time, including fractional-offset zones.
                    val local = Calendar.getInstance(zone).apply { timeInMillis = cursor }
                    val minute = local.get(Calendar.MINUTE)
                    val needsRounding = minute % 15 != 0 || local.get(Calendar.SECOND) != 0 || local.get(Calendar.MILLISECOND) != 0
                    if (needsRounding) local.add(Calendar.MINUTE, 15 - minute % 15)
                    local.set(Calendar.SECOND, 0); local.set(Calendar.MILLISECOND, 0)
                    val rounded = local.timeInMillis
                    if (rounded + durationMinutes * MINUTE_MS <= until) openings += CalendarOpening(rounded, rounded + durationMinutes * MINUTE_MS)
                }
                blocked.forEach { range ->
                    if (range.endsAt > cursor && range.startsAt < end) {
                        offer(minOf(range.startsAt, end))
                        cursor = maxOf(cursor, range.endsAt)
                    }
                }
                if (cursor < end) offer(end)
            }
            CalendarPlanDay(day, daily, conflicts, openings.take(3))
        }
    }
}
