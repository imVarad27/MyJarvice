package com.example.myjarvice.data

import java.util.TimeZone

internal data class RehearsalOption(
    val startsAt: Long,
    val endsAt: Long,
    val protectedFrom: Long,
    val protectedUntil: Long,
    val overrunEndsAt: Long,
    val survivesOverrun: Boolean
)

internal data class TimeRehearsalResult(
    val available: Boolean,
    val durationMinutes: Int,
    val bufferMinutes: Int,
    val overrunMinutes: Int,
    val options: List<RehearsalOption>
) {
    val resilientCount: Int get() = options.count { it.survivesOverrun }
}

/** A bounded what-if calculation. No prompts, learned scores, storage, reservations or side effects. */
internal object TimeRehearsal {
    val buffers = listOf(0, 10, 20, 30)
    private const val MINUTE_MS = 60_000L

    fun evaluate(snapshot: CalendarSnapshot, now: Long, durationMinutes: Int, bufferMinutes: Int,
        zone: TimeZone = TimeZone.getDefault()): TimeRehearsalResult {
        require(durationMinutes in CalendarWeekPlanner.durations)
        require(bufferMinutes in buffers)
        val overrunMinutes = durationMinutes * 3 / 2
        if (!CalendarWeekPlanner.isComplete(snapshot, zone)) {
            return TimeRehearsalResult(false, durationMinutes, bufferMinutes, overrunMinutes, emptyList())
        }
        val required = (durationMinutes + bufferMinutes * 2) * MINUTE_MS
        val stressed = (overrunMinutes + bufferMinutes * 2) * MINUTE_MS
        val options = CalendarWeekPlanner.plan(snapshot, now, durationMinutes, zone).flatMap { day ->
            // One earliest candidate per qualifying gap, at most three per day. Not a count of all starts.
            day.freeWindows.filter { it.endsAt - it.startsAt >= required }.take(3).map { gap ->
                val start = gap.startsAt + bufferMinutes * MINUTE_MS
                RehearsalOption(start, start + durationMinutes * MINUTE_MS,
                    gap.startsAt, gap.startsAt + required, start + overrunMinutes * MINUTE_MS,
                    gap.endsAt - gap.startsAt >= stressed)
            }
        }
        return TimeRehearsalResult(true, durationMinutes, bufferMinutes, overrunMinutes, options)
    }
}
