package com.example.myjarvice.data

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.database.Cursor
import android.net.Uri
import android.provider.CalendarContract
import androidx.core.content.ContextCompat
import java.util.TimeZone

data class CalendarAgendaItem(
    val eventId: Long,
    val title: String,
    val startsAt: Long,
    val endsAt: Long,
    val allDay: Boolean,
    val location: String,
    val blocksTime: Boolean = true
)

/** Read-only Android calendar instances. Event text stays in the app process. */
class CalendarAgendaRepository internal constructor(
    private val context: Context,
    private val readInstances: (Uri, Array<String>, String) -> Cursor?
) {
    constructor(context: Context) : this(context, { uri, projection, order ->
        context.contentResolver.query(uri, projection, null, null, order)
    })
    /** Read a padded range for UTC all-day dates, then filter by local date in the planner. */
    fun week(now: Long = System.currentTimeMillis()): CalendarSnapshot {
        val zone = TimeZone.getDefault()
        val days = CalendarWeekPlanner.days(now, zone)
        return readLocalWindow(LocalDayWindow(days.first().startsAt, days.last().endsAtExclusive), zone)
    }

    fun today(now: Long = System.currentTimeMillis()): List<CalendarAgendaItem> {
        val zone = TimeZone.getDefault()
        val snapshot = readLocalWindow(CalendarWeekPlanner.days(now, zone).first(), zone)
        check(!snapshot.incomplete) { "Today's calendar data is incomplete." }
        return snapshot.events
    }

    private fun readLocalWindow(window: LocalDayWindow, zone: TimeZone): CalendarSnapshot {
        val queryWindow = LocalDayWindow(window.startsAt - 86_400_000L, window.endsAtExclusive + 86_400_000L)
        val snapshot = query(queryWindow)
        return snapshot.copy(incomplete = snapshot.incomplete || snapshot.events.any { event ->
            val interval = CalendarWeekPlanner.interval(event, zone)
            interval.endsAt <= interval.startsAt
        }, events = snapshot.events.filter { event ->
            val interval = CalendarWeekPlanner.interval(event, zone)
            interval.startsAt < window.endsAtExclusive && interval.endsAt > window.startsAt
        })
    }

    private fun query(window: LocalDayWindow): CalendarSnapshot {
        check(ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED) {
            "Calendar permission is required."
        }
        val uriBuilder = CalendarContract.Instances.CONTENT_URI.buildUpon()
        ContentUris.appendId(uriBuilder, window.startsAt)
        ContentUris.appendId(uriBuilder, window.endsAtExclusive)
        val projection = arrayOf(
            CalendarContract.Instances.EVENT_ID,
            CalendarContract.Instances.TITLE,
            CalendarContract.Instances.BEGIN,
            CalendarContract.Instances.END,
            CalendarContract.Instances.ALL_DAY,
            CalendarContract.Instances.EVENT_LOCATION,
            CalendarContract.Instances.VISIBLE,
            CalendarContract.Instances.STATUS,
            CalendarContract.Instances.AVAILABILITY
        )
        return readInstances(
            uriBuilder.build(), projection,
            "${CalendarContract.Instances.BEGIN} ASC"
        )?.use { cursor ->
            val eventId = cursor.getColumnIndexOrThrow(CalendarContract.Instances.EVENT_ID)
            val title = cursor.getColumnIndexOrThrow(CalendarContract.Instances.TITLE)
            val begin = cursor.getColumnIndexOrThrow(CalendarContract.Instances.BEGIN)
            val end = cursor.getColumnIndexOrThrow(CalendarContract.Instances.END)
            val allDay = cursor.getColumnIndexOrThrow(CalendarContract.Instances.ALL_DAY)
            val location = cursor.getColumnIndexOrThrow(CalendarContract.Instances.EVENT_LOCATION)
            val visible = cursor.getColumnIndexOrThrow(CalendarContract.Instances.VISIBLE)
            val status = cursor.getColumnIndexOrThrow(CalendarContract.Instances.STATUS)
            val availability = cursor.getColumnIndexOrThrow(CalendarContract.Instances.AVAILABILITY)
            var incomplete = false
            var scanned = 0
            val events = buildList {
                while (cursor.moveToNext()) {
                    scanned++
                    if (scanned > 2_000 || size >= CalendarWeekPlanner.MAX_EVENTS) { incomplete = true; break }
                    if (cursor.getInt(visible) != 1 || cursor.getInt(status) == CalendarContract.Events.STATUS_CANCELED) continue
                    if (cursor.isNull(eventId) || cursor.getLong(eventId) <= 0 || cursor.isNull(begin) || cursor.isNull(end) || cursor.getLong(end) <= cursor.getLong(begin)) {
                        incomplete = true; continue
                    }
                    add(CalendarAgendaItem(
                        eventId = cursor.getLong(eventId),
                        title = cursor.getString(title)?.trim().orEmpty().take(240).ifBlank { "Untitled event" },
                        startsAt = cursor.getLong(begin),
                        endsAt = cursor.getLong(end),
                        allDay = cursor.getInt(allDay) == 1,
                        location = cursor.getString(location)?.trim().orEmpty().take(300),
                        blocksTime = cursor.isNull(availability) || cursor.getInt(availability) != CalendarContract.Events.AVAILABILITY_FREE
                    ))
                }
            }.distinctBy { Triple(it.eventId, it.startsAt, it.endsAt) }
            CalendarSnapshot(events, incomplete)
        } ?: error("Calendar provider did not return a readable schedule.")
    }
}
