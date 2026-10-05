package com.example.myjarvice.data

import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream

class ActionTimelineTest {
    private val time = 1_791_158_400_000L

    @Test fun journalPersistsOnlyStaticMetadataAndKeepsLatest500() {
        var persisted: String? = null
        val journal = PhoneActivityJournal({ persisted }, { persisted = it }, { time })
        repeat(520) { journal.record("tool.calculate", "completed") }
        val rows = journal.recent()
        assertEquals(500, rows.size)
        assertEquals(21L, rows.first().id)
        assertEquals(520L, rows.last().id)
        assertTrue(rows.all { it.source == "phone" && it.summary == "Calculator · Completed" })
        assertTrue(persisted.orEmpty().toByteArray().size < ActionTimeline.MAX_FILE_BYTES)
        // An arbitrary name must not become a stored prompt or argument.
        journal.record("private email body or password", "secret content")
        assertFalse(persisted.orEmpty().contains("private email"))
        assertFalse(persisted.orEmpty().contains("secret content"))
        assertEquals("model.tool", journal.recent().last().actionType)
    }

    @Test fun clearRemovesOnlyInjectedJournalAndLaterActionsStillRecord() {
        var persisted: String? = null
        val journal = PhoneActivityJournal({ persisted }, { persisted = it }, { time })
        journal.record("tool.remember", "completed")
        journal.clear()
        assertTrue(journal.recent().isEmpty())
        journal.record("tool.clock", "completed")
        assertEquals(1, journal.recent().size)
    }

    @Test fun corruptedDataIsNotSilentlyOverwritten() {
        var persisted: String? = "broken json"
        val journal = PhoneActivityJournal({ persisted }, { persisted = it }, { time })
        assertTrue(runCatching { journal.recent() }.isFailure)
        assertTrue(runCatching { journal.record("tool.clock", "completed") }.isFailure)
        assertEquals("broken json", persisted)
        journal.clear()
        assertEquals("[]", persisted)
    }

    @Test fun persistedSummariesAreRegeneratedAndPcRowsCannotBeImported() {
        val row = ActionTimeline.localEvent(1, time, "tool.clock", "completed")
        val encoded = ActionTimeline.encode(listOf(row.copy(summary = "Sensitive content")))
        assertFalse(ActionTimeline.decode(encoded).single().summary.contains("Sensitive"))
        assertTrue(runCatching { ActionTimeline.decode(ActionTimeline.encode(listOf(row.copy(source = "pc")))) }.isFailure)
        assertTrue(runCatching { ActionTimeline.decode(ActionTimeline.encode(listOf(row.copy(id = Long.MAX_VALUE)))) }.isFailure)
    }

    @Test fun recordsFromDifferentInstancesShareMonotonicIds() {
        var persisted: String? = null
        val first = PhoneActivityJournal({ persisted }, { persisted = it }, { time })
        val second = PhoneActivityJournal({ persisted }, { persisted = it }, { time })
        first.record("tool.clock", "completed")
        second.record("tool.clock", "completed")
        assertEquals(listOf(1L, 2L), first.recent().map { it.id })
    }

    @Test fun exhaustedIdsCannotWriteAnUnreadableJournal() {
        var persisted: String? = ActionTimeline.encode(listOf(
            ActionTimeline.localEvent(Long.MAX_VALUE - 1, time, "tool.clock", "completed")))
        val original = persisted
        val journal = PhoneActivityJournal({ persisted }, { persisted = it }, { time })
        assertTrue(runCatching { journal.record("tool.clock", "completed") }.isFailure)
        assertEquals(original, persisted)
    }

    @Test fun timezoneSortingAndSourceIdentityDoNotMixPcAndPhoneIds() {
        val phone = ActionAuditEvent(1, "2026-10-05T10:01:00+05:30", "tool.clock", "completed", "Phone", "phone")
        val pc = ActionAuditEvent(1, "2026-10-05T04:30:00+00:00", "pc_status", "completed", "PC")
        val visible = ActionTimeline.visible(listOf(pc, phone, pc))
        assertEquals(2, visible.size)
        assertEquals("phone", visible.first().source)
        assertEquals(1, ActionTimeline.visible(visible, source = ActivitySource.PC).size)
        assertEquals(1, ActionTimeline.visible(visible, query = "phone clock").size)
        assertEquals(ActionTimeline.epochMillis("2026-10-05T10:00:00+05:30"), ActionTimeline.epochMillis("2026-10-05T04:30:00Z"))
        assertEquals(ActionTimeline.epochMillis("2026-10-05T04:30:00.123Z"), ActionTimeline.epochMillis("2026-10-05T04:30:00.123456+00:00"))
    }

    @Test fun invalidTimestampsAndOutcomeFiltersAreHonest() {
        assertNull(ActionTimeline.epochMillis("2026-99-01T00:00:00Z"))
        assertNull(ActionTimeline.epochMillis("2026-10-05T04:30:00Z trailing"))
        assertNull(ActionTimeline.epochMillis("2026-10-05T04:30:00"))
        val rows = listOf("completed", "prepared", "awaiting_approval", "failed", "blocked").mapIndexed { i, status ->
            ActionTimeline.localEvent(i + 1L, time, "tool.clock", status)
        }
        assertEquals(2, ActionTimeline.visible(rows, outcome = ActivityOutcome.ATTENTION).size)
        assertEquals(2, ActionTimeline.visible(rows, outcome = ActivityOutcome.PREPARED).size)
        assertEquals(1, ActionTimeline.visible(rows, outcome = ActivityOutcome.COMPLETED).size)
        assertTrue(ActionTimeline.outcomeLabel("approved").contains("not a delivery confirmation"))
        assertEquals("Status unavailable", ActionTimeline.outcomeLabel("unknown"))
    }

    @Test fun phoneHandoffsArePreparedButLocalReadsAndWritesAreCompleted() {
        listOf("CALL", "WHATSAPP", "SET_ALARM", "SET_TIMER", "OPEN_APP", "NAVIGATE").forEach {
            assertEquals("prepared", ActionTimeline.phoneSuccessOutcome(it))
        }
        listOf("FLASHLIGHT", "DEVICE_STATUS", "ADD_LOCAL_TASK", "SHOW_LOCAL_TASKS").forEach {
            assertEquals("completed", ActionTimeline.phoneSuccessOutcome(it))
        }
        assertEquals("Add phone task", ActionTimeline.label("device.add_local_task"))
    }

    @Test fun streamsAndJsonCannotExceedRetentionBounds() {
        val oversized = ByteArray(ActionTimeline.MAX_FILE_BYTES + 1)
        assertTrue(runCatching { PhoneActivityJournal.readBounded(ByteArrayInputStream(oversized)) }.isFailure)
        assertTrue(runCatching { ActionTimeline.decode("x".repeat(ActionTimeline.MAX_FILE_BYTES + 1)) }.isFailure)
    }
}
