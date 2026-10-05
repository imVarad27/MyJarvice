package com.example.myjarvice.data

import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import java.text.ParsePosition
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.io.InputStream
import java.io.ByteArrayOutputStream

enum class ActivitySource(val title: String) { ALL("All"), PHONE("Phone"), PC("PC") }
enum class ActivityOutcome(val title: String) { ALL("All outcomes"), ATTENTION("Needs attention"), COMPLETED("Completed"), PREPARED("Prepared") }

/** Metadata only: no caller-supplied summary, argument, prompt or tool observation. */
object ActionTimeline {
    const val MAX_EVENTS = 500
    const val MAX_FILE_BYTES = 512 * 1024
    private val json = Json { ignoreUnknownKeys = true }
    private val labels = mapOf(
        "calculate" to "Calculator", "clock" to "Phone clock", "phone_status" to "Phone status",
        "search_library" to "Memory & documents", "search_inbox" to "Saved inbox",
        "remember" to "Save memory", "list_memories" to "Read memories",
        "add_task" to "Add task", "list_tasks" to "Read tasks", "complete_task" to "Complete task",
        "open_app" to "Open app", "navigate" to "Directions", "flashlight" to "Flashlight",
        "set_alarm" to "Prepare alarm", "set_timer" to "Prepare timer",
        "open_phone_app" to "Open phone app", "navigate_phone" to "Phone directions",
        "set_phone_alarm" to "Prepare phone alarm", "set_phone_timer" to "Prepare phone timer",
        "call_phone" to "Prepare phone call", "whatsapp_phone" to "Prepare WhatsApp draft",
        "pc_status" to "PC status", "open_pc_app" to "Open PC app", "open_pc_folder" to "Open PC folder",
        "pc_volume" to "PC volume", "pc_media" to "PC media", "list_reminders" to "Read reminders",
        "add_reminder" to "Create reminder", "set_user_name" to "Save preferred name",
        "save_contact_email" to "Save email contact", "search_documents" to "Search PC documents",
        "search_web" to "Web search", "draft_email" to "Prepare email draft",
        "email.draft" to "Email draft", "email.approval" to "Email approval", "email.send" to "Send email",
        "file.open" to "Open PC item", "file.browse" to "Browse PC folder", "file.upload" to "Upload to PC",
        "file.download" to "Download from PC", "model.tool" to "Tool validation",
        "device.call" to "Phone call", "device.whatsapp" to "WhatsApp draft",
        "device.open_app" to "Open phone app", "device.navigate" to "Phone directions",
        "device.flashlight" to "Flashlight", "device.set_alarm" to "Prepare phone alarm",
        "device.set_timer" to "Prepare phone timer", "device.invalid" to "Phone action validation",
        "device.device_status" to "Phone status", "device.add_local_task" to "Add phone task",
        "device.show_local_tasks" to "Read phone tasks"
    )
    private val outcomes = setOf("completed", "prepared", "failed", "blocked", "rejected", "no_data",
        "awaiting_approval", "approved", "discarded", "paused", "cancelled", "sent", "started", "outcome_unknown")

    fun label(type: String): String = labels[type] ?: labels[type.removePrefix("tool.")] ?: "Assistant action"

    /** Intent hand-offs do not confirm what happened in the other app. */
    fun phoneSuccessOutcome(type: String): String = when (type) {
        "FLASHLIGHT", "DEVICE_STATUS", "ADD_LOCAL_TASK", "SHOW_LOCAL_TASKS" -> "completed"
        else -> "prepared"
    }

    fun outcomeLabel(outcome: String): String = when (outcome) {
        "completed" -> "Completed"
        "prepared" -> "Prepared · check the opened app"
        "awaiting_approval" -> "Waiting for approval"
        "approved" -> "Approved · not a delivery confirmation"
        "sent" -> "Submitted to mail server"
        "failed" -> "Failed"
        "blocked" -> "Blocked for safety"
        "rejected" -> "Invalid request rejected"
        "paused" -> "Stopped while paused"
        "cancelled", "discarded" -> "Cancelled"
        "no_data" -> "No matching data"
        "started" -> "Started · completion not confirmed"
        "outcome_unknown" -> "Outcome not confirmed"
        else -> "Status unavailable"
    }

    fun localEvent(id: Long, time: Long, actionType: String, outcome: String): ActionAuditEvent {
        val safeType = actionType.takeIf { it in labels || it.removePrefix("tool.") in labels } ?: "model.tool"
        val safeOutcome = outcome.takeIf { it in outcomes } ?: "rejected"
        val stamp = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }.format(Date(time))
        return ActionAuditEvent(id, stamp, safeType, safeOutcome,
            "${label(safeType)} · ${outcomeLabel(safeOutcome)}", "phone")
    }

    fun encode(events: List<ActionAuditEvent>): String = json.encodeToString(events.takeLast(MAX_EVENTS))

    fun decode(text: String): List<ActionAuditEvent> {
        require(text.toByteArray(Charsets.UTF_8).size <= MAX_FILE_BYTES) { "Activity file is too large." }
        val rows = json.decodeFromString<List<ActionAuditEvent>>(text)
        require(rows.size <= MAX_EVENTS) { "Activity file exceeds its retention limit." }
        // Regenerate static summaries; a tampered log cannot introduce prompts/content.
        return rows.map { row ->
            require(row.source == "phone" && row.id in 1 until Long.MAX_VALUE) { "Invalid phone activity record." }
            val time = epochMillis(row.createdAt) ?: error("Invalid phone activity timestamp.")
            localEvent(row.id, time, row.actionType, row.outcome)
        }
    }

    fun epochMillis(value: String): Long? {
        if (value.length > 40) return null
        val normalized = value.replace(Regex("(\\.\\d{3})\\d+(?=Z|[+-]\\d{2}:\\d{2}$)"), "$1")
        for (pattern in listOf("yyyy-MM-dd'T'HH:mm:ss.SSSXXX", "yyyy-MM-dd'T'HH:mm:ssXXX")) {
            val position = ParsePosition(0)
            val date = SimpleDateFormat(pattern, Locale.US).apply { isLenient = false }.parse(normalized, position)
            if (date != null && position.index == normalized.length) return date.time
        }
        return null
    }

    fun visible(events: List<ActionAuditEvent>, query: String = "", source: ActivitySource = ActivitySource.ALL,
                outcome: ActivityOutcome = ActivityOutcome.ALL): List<ActionAuditEvent> {
        val needle = query.trim().take(200).lowercase()
        return events.distinctBy { "${it.source}:${it.id}" }.filter { event ->
            val sourceMatches = source == ActivitySource.ALL || event.source == source.name.lowercase()
            val outcomeMatches = when (outcome) {
                ActivityOutcome.ALL -> true
                ActivityOutcome.ATTENTION -> event.outcome in setOf("failed", "blocked", "rejected", "paused", "outcome_unknown")
                ActivityOutcome.COMPLETED -> event.outcome in setOf("completed", "sent")
                ActivityOutcome.PREPARED -> event.outcome in setOf("prepared", "awaiting_approval", "approved", "started")
            }
            sourceMatches && outcomeMatches && (needle.isEmpty() ||
                listOf(label(event.actionType), outcomeLabel(event.outcome), event.summary).any { needle in it.lowercase() })
        }.sortedWith(compareByDescending<ActionAuditEvent> { epochMillis(it.createdAt) ?: Long.MIN_VALUE }.thenByDescending { it.id })
    }
}

/** Injectable persistence lets JVM tests verify retention, corruption and privacy. */
internal class PhoneActivityJournal(private val read: () -> String?, private val write: (String) -> Unit,
                                    private val now: () -> Long = System::currentTimeMillis) {
    fun recent(): List<ActionAuditEvent> = synchronized(lock) { read()?.let(ActionTimeline::decode).orEmpty() }
    fun record(type: String, outcome: String): ActionAuditEvent = synchronized(lock) {
        val rows = recent()
        val previousId = rows.maxOfOrNull { it.id } ?: 0
        require(previousId < Long.MAX_VALUE - 1) { "Activity record IDs are exhausted." }
        val id = previousId + 1
        val event = ActionTimeline.localEvent(id, now(), type, outcome)
        write(ActionTimeline.encode((rows + event).takeLast(ActionTimeline.MAX_EVENTS)))
        event
    }
    fun clear() = synchronized(lock) { write("[]") }
    companion object {
        private val lock = Any()
        fun readBounded(input: InputStream): ByteArray {
            val output = ByteArrayOutputStream()
            val buffer = ByteArray(4096)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                require(output.size() + count <= ActionTimeline.MAX_FILE_BYTES) { "Activity response is too large." }
                output.write(buffer, 0, count)
            }
            return output.toByteArray()
        }
    }
}
