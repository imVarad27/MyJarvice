package com.example.myjarvice.data

import org.junit.Assert.*
import org.junit.Test
import java.util.Calendar

class PhoneContextAggregatorTest {

    private val now = Calendar.getInstance().apply {
        set(2026, Calendar.OCTOBER, 31, 10, 0, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis
    private val today = LocalDayWindow.containing(now)

    private class FakePhoneDataProvider(
        private val taskList: List<LocalTask> = emptyList(),
        private val routineList: List<LocalRoutine> = emptyList(),
        private val calendarList: List<CalendarAgendaItem> = emptyList(),
        private val inboxList: List<RememberItem> = emptyList(),
        private val knowledgeList: List<KnowledgeEntry> = emptyList(),
        private val calendarGranted: Boolean = true
    ) : PhoneDataProvider {
        override fun tasks() = taskList
        override fun routines() = routineList
        override fun calendarEvents() = calendarList
        override fun inboxItems() = inboxList
        override fun knowledgeEntries() = knowledgeList
        override fun isCalendarPermissionGranted() = calendarGranted
    }

    @Test
    fun `search finds matches across tasks routines calendar and memories`() {
        val tasks = listOf(
            LocalTask("t1", "Buy dental floss", createdAt = now, notes = "Need mint flavor"),
            LocalTask("t2", "Review weekly pull requests", createdAt = now)
        )
        val routines = listOf(
            LocalRoutine("r1", "Morning routine", listOf("Stretch", "Drink dental rinse", "Check mail"), now, now)
        )
        val calendar = listOf(
            CalendarAgendaItem(1L, "Dental Checkup", startsAt = now + 3600_000L, endsAt = now + 7200_000L, allDay = false, location = "City Clinic")
        )
        val inbox = listOf(
            RememberItem("i1", RememberKind.TEXT, "Dentist clinic phone number", "Office number is 555-0199", "555-0199", now)
        )
        val knowledge = listOf(
            KnowledgeEntry("k1", "Saved memory", "Prefers morning dentist appointments", true)
        )

        val aggregator = PhoneContextAggregator(
            FakePhoneDataProvider(
                taskList = tasks,
                routineList = routines,
                calendarList = calendar,
                inboxList = inbox,
                knowledgeList = knowledge,
                calendarGranted = true
            )
        )

        val hits = aggregator.search("dental", now)
        assertTrue("Expected matching results for 'dental'", hits.isNotEmpty())
        assertTrue(hits.any { it.source.contains("Calendar") || it.source.contains("Phone task") || it.source.contains("Routine") })
    }

    @Test
    fun `search excludes calendar events when calendar permission is not granted`() {
        val calendar = listOf(
            CalendarAgendaItem(1L, "Secret Project Sync", startsAt = now + 3600_000L, endsAt = now + 7200_000L, allDay = false, location = "Room A")
        )

        val aggregator = PhoneContextAggregator(
            FakePhoneDataProvider(
                calendarList = calendar,
                calendarGranted = false
            )
        )

        val hits = aggregator.search("Secret Project", now)
        assertTrue("Calendar events must not be retrieved when permission is revoked", hits.isEmpty())
    }

    @Test
    fun `querySchedule combines calendar events and tasks for today`() {
        val tasks = listOf(
            LocalTask("t1", "Submit quarterly report", createdAt = now, dueAt = today.startsAt + 1000L)
        )
        val calendar = listOf(
            CalendarAgendaItem(10L, "Design Review", startsAt = today.startsAt + 3600_000L * 14, endsAt = today.startsAt + 3600_000L * 15, allDay = false, location = "HQ Room 1")
        )

        val aggregator = PhoneContextAggregator(
            FakePhoneDataProvider(
                taskList = tasks,
                calendarList = calendar,
                calendarGranted = true
            )
        )

        val schedule = aggregator.querySchedule("today", now)
        assertTrue(schedule.contains("Design Review"))
        assertTrue(schedule.contains("Submit quarterly report"))
        assertTrue(schedule.contains("HQ Room 1"))
    }

    @Test
    fun `querySchedule informs user when calendar permission is not granted`() {
        val tasks = listOf(
            LocalTask("t1", "Buy groceries", createdAt = now, dueAt = today.startsAt + 1000L)
        )

        val aggregator = PhoneContextAggregator(
            FakePhoneDataProvider(
                taskList = tasks,
                calendarGranted = false
            )
        )

        val schedule = aggregator.querySchedule("today", now)
        assertTrue(schedule.contains("Permission not granted"))
        assertTrue(schedule.contains("Buy groceries"))
    }

    @Test
    fun `querySchedule provides clear empty state when nothing is scheduled`() {
        val aggregator = PhoneContextAggregator(FakePhoneDataProvider(calendarGranted = true))

        val schedule = aggregator.querySchedule("today", now)
        assertTrue(schedule.contains("No events scheduled"))
        assertTrue(schedule.contains("No open tasks due Today"))
    }
}
