package com.example.myjarvice.data

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.provider.CalendarContract
import androidx.core.content.ContextCompat

data class CalendarAgendaItem(
    val eventId: Long,
    val title: String,
    val startsAt: Long,
    val endsAt: Long,
    val allDay: Boolean,
    val location: String
)

/** Read-only access to today's Android calendar instances. Event text stays in the app process. */
class CalendarAgendaRepository(private val context: Context) {
    fun today(now: Long = System.currentTimeMillis()): List<CalendarAgendaItem> {
        check(ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED) {
            "Calendar permission is required."
        }
        val window = LocalDayWindow.containing(now)
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
            CalendarContract.Instances.STATUS
        )
        return context.contentResolver.query(
            uriBuilder.build(), projection, null, null,
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
            buildList {
                while (cursor.moveToNext()) {
                    if (cursor.getInt(visible) != 1 || cursor.getInt(status) == CalendarContract.Events.STATUS_CANCELED) continue
                    add(CalendarAgendaItem(
                        eventId = cursor.getLong(eventId),
                        title = cursor.getString(title)?.trim().orEmpty().ifBlank { "Untitled event" },
                        startsAt = cursor.getLong(begin),
                        endsAt = cursor.getLong(end),
                        allDay = cursor.getInt(allDay) == 1,
                        location = cursor.getString(location)?.trim().orEmpty()
                    ))
                }
            }.distinctBy { Triple(it.eventId, it.startsAt, it.endsAt) }
        }.orEmpty()
    }
}
