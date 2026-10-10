package com.example.myjarvice.data

import android.content.Context
import android.content.ContextWrapper
import android.content.pm.PackageManager
import android.database.MatrixCursor
import android.provider.CalendarContract
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.*
import org.junit.Test
import java.util.Calendar

/** Injected synthetic cursors only; does not read the real provider or request any permissions. */
class CalendarAgendaRepositoryTest {
    private val base = ApplicationProvider.getApplicationContext<Context>()
    private val now = Calendar.getInstance().apply { set(2026, Calendar.OCTOBER, 10, 8, 0, 0); set(Calendar.MILLISECOND, 0) }.timeInMillis
    private fun context(granted: Boolean = true) = object : ContextWrapper(base) {
        override fun checkPermission(permission: String, pid: Int, uid: Int): Int =
            if (granted) PackageManager.PERMISSION_GRANTED else PackageManager.PERMISSION_DENIED
        override fun checkSelfPermission(permission: String): Int =
            if (granted) PackageManager.PERMISSION_GRANTED else PackageManager.PERMISSION_DENIED
    }
    private fun row(id: Long, visible: Int = 1, status: Int = CalendarContract.Events.STATUS_CONFIRMED,
        availability: Int? = CalendarContract.Events.AVAILABILITY_BUSY, end: Long = now + 7_200_000) =
        arrayOf<Any?>(id, "Synthetic $id", now + 3_600_000, end, 0, "Synthetic room", visible, status, availability)

    @Test fun queryIsReadOnlyBoundedPaddedAndFiltersHiddenCancelledAndDuplicates() {
        var reads = 0
        val repository = CalendarAgendaRepository(context()) { uri, columns, order ->
            reads++
            assertEquals("com.android.calendar", uri.authority)
            val days = CalendarWeekPlanner.days(now)
            assertTrue(uri.pathSegments.takeLast(2)[0].toLong() < days.first().startsAt)
            assertTrue(uri.lastPathSegment!!.toLong() > days.last().endsAtExclusive)
            assertEquals("${CalendarContract.Instances.BEGIN} ASC", order)
            MatrixCursor(columns).apply {
                addRow(row(1)); addRow(row(1)); addRow(row(2, visible = 0))
                addRow(row(3, status = CalendarContract.Events.STATUS_CANCELED))
                addRow(row(4, availability = CalendarContract.Events.AVAILABILITY_FREE))
                addRow(row(5, availability = CalendarContract.Events.AVAILABILITY_TENTATIVE))
                addRow(row(6, availability = null))
            }
        }
        val snapshot = repository.week(now)
        assertEquals(1, reads)
        assertFalse(snapshot.incomplete)
        assertEquals(listOf(1L, 4L, 5L, 6L), snapshot.events.map { it.eventId })
        assertFalse(snapshot.events.first { it.eventId == 4L }.blocksTime)
        assertTrue(snapshot.events.first { it.eventId == 5L }.blocksTime)
        assertTrue(snapshot.events.first { it.eventId == 6L }.blocksTime)
    }

    @Test fun deniedPermissionNeverQueriesAndNullProviderIsNotAnEmptyWeek() {
        var reads = 0
        val denied = CalendarAgendaRepository(context(false)) { _, _, _ -> reads++; null }
        try { denied.week(now); fail("Denied permission accepted") } catch (_: IllegalStateException) { }
        assertEquals(0, reads)
        val unreadable = CalendarAgendaRepository(context()) { _, _, _ -> null }
        try { unreadable.week(now); fail("Unavailable provider reported as clear calendar") } catch (_: IllegalStateException) { }
    }

    @Test fun malformedAndOverLimitResultsSuppressOpenings() {
        for (count in listOf(1, 501, 2001)) {
            val repository = CalendarAgendaRepository(context()) { _, columns, _ ->
                MatrixCursor(columns).apply { repeat(count) { index ->
                    addRow(when (count) {
                        1 -> row(1, end = now)
                        2001 -> row(index.toLong() + 1, visible = 0)
                        else -> row(index.toLong() + 1)
                    })
                } }
            }
            val snapshot = repository.week(now)
            assertTrue(snapshot.incomplete)
            assertTrue(snapshot.events.size <= 500)
            assertTrue(CalendarWeekPlanner.plan(snapshot, now).all { it.openings.isEmpty() })
        }
    }
}
