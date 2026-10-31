package com.example.myjarvice.data

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import java.text.DateFormat
import java.util.Date
import java.util.TimeZone

/** Interface providing phone data for offline RAG indexing. */
interface PhoneDataProvider {
    fun tasks(): List<LocalTask>
    fun routines(): List<LocalRoutine>
    fun calendarEvents(): List<CalendarAgendaItem>
    fun inboxItems(): List<RememberItem>
    fun knowledgeEntries(): List<KnowledgeEntry>
    fun isCalendarPermissionGranted(): Boolean
    fun contacts(): List<ContactCard>
    fun isContactsPermissionGranted(): Boolean
}

/** Default implementation binding to local Android stores and permissioned providers. */
class AndroidPhoneDataProvider(private val context: Context) : PhoneDataProvider {
    override fun tasks(): List<LocalTask> = LocalTaskStore(context).tasks()
    override fun routines(): List<LocalRoutine> = LocalRoutineStore(context).routines()
    override fun calendarEvents(): List<CalendarAgendaItem> {
        return if (isCalendarPermissionGranted()) {
            runCatching { CalendarAgendaRepository(context).week().events }.getOrDefault(emptyList())
        } else emptyList()
    }
    override fun inboxItems(): List<RememberItem> = RememberInboxStore(context).items()
    override fun knowledgeEntries(): List<KnowledgeEntry> = LocalKnowledgeStore(context).entries()
    override fun isCalendarPermissionGranted(): Boolean {
        return ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED
    }
    override fun contacts(): List<ContactCard> {
        return if (isContactsPermissionGranted()) {
            AndroidContactsDataProvider(context).queryAllContacts()
        } else emptyList()
    }
    override fun isContactsPermissionGranted(): Boolean {
        return ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED
    }
}

/**
 * Aggregates private phone data (tasks, routines, calendar events, saved inbox items,
 * and personal memories/documents) for unified offline RAG retrieval.
 */
class PhoneContextAggregator internal constructor(
    private val provider: PhoneDataProvider
) {
    constructor(context: Context) : this(AndroidPhoneDataProvider(context))

    /**
     * Unified lexical retrieval across Tasks, Routines, Calendar events,
     * Saved Inbox items, and Personal Memories/Documents.
     */
    fun search(query: String, now: Long = System.currentTimeMillis()): List<KnowledgeHit> {
        val entries = mutableListOf<KnowledgeEntry>()

        // 1. Tasks
        provider.tasks().forEach { task ->
            val dueStr = task.dueAt?.let { " · Due: " + DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(it)) } ?: ""
            val repStr = if (task.repeats) " · ${task.recurrence.label}" else ""
            val statusStr = if (task.completed) "Completed" else "Open"
            val text = "Task ($statusStr): ${task.title}$dueStr$repStr" + if (task.notes.isNotBlank()) "\nNotes: ${task.notes}" else ""
            entries.add(KnowledgeEntry(id = "task-${task.id}", name = "Phone task · ${task.title}", text = text, memory = false))
        }

        // 2. Routines
        provider.routines().forEach { routine ->
            val stepsStr = routine.steps.mapIndexed { idx, step -> "${idx + 1}. $step" }.joinToString(", ")
            val text = "Routine: ${routine.title}\nSteps: $stepsStr"
            entries.add(KnowledgeEntry(id = "routine-${routine.id}", name = "Routine · ${routine.title}", text = text, memory = false))
        }

        // 3. Calendar events (if permission granted)
        if (provider.isCalendarPermissionGranted()) {
            provider.calendarEvents().forEach { event ->
                val dateStr = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(event.startsAt))
                val locStr = if (event.location.isNotBlank()) " · Location: ${event.location}" else ""
                val text = "Calendar event: ${event.title}\nTime: $dateStr$locStr"
                entries.add(KnowledgeEntry(id = "cal-${event.eventId}-${event.startsAt}", name = "Calendar · ${event.title}", text = text, memory = false))
            }
        }

        // 4. Saved Inbox
        provider.inboxItems().forEach { item ->
            val content = (item.searchableText.ifBlank { item.summary }).take(3000)
            entries.add(KnowledgeEntry(id = "inbox-${item.id}", name = "Saved inbox · ${item.title}", text = content, memory = false))
        }

        // 5. Personal memories & documents
        entries.addAll(provider.knowledgeEntries().filter { it.enabled })

        // 6. Contacts (if permission granted)
        if (provider.isContactsPermissionGranted()) {
            provider.contacts().forEach { contact ->
                val nums = if (contact.numbers.isNotEmpty()) " · Phone: " + contact.numbers.joinToString(", ") else ""
                val mails = if (contact.emails.isNotEmpty()) " · Email: " + contact.emails.joinToString(", ") else ""
                val text = "Contact: ${contact.name}$nums$mails"
                entries.add(KnowledgeEntry(id = "contact-${contact.id}", name = "Contact · ${contact.name}", text = text, memory = false))
            }
        }

        return LocalKnowledgeStore.rank(query, entries)
    }

    /**
     * Dedicated contact search returning formatted contact cards.
     * Searches by name, phone number (reverse lookup), or email.
     */
    fun searchContacts(query: String): String {
        if (!provider.isContactsPermissionGranted()) {
            return "Contacts permission is required to read contacts. You can enable it in Android Settings."
        }
        val q = query.trim().lowercase()
        if (q.isBlank()) return "Please provide a name, phone number, or email to search."

        val queryDigits = q.filter { it.isDigit() }
        val matchesNumber = queryDigits.length >= 3

        val matched = provider.contacts().asSequence()
            .filter { contact ->
                val nameMatch = contact.name.lowercase().contains(q)
                val emailMatch = contact.emails.any { it.lowercase().contains(q) }
                val phoneMatch = matchesNumber && contact.numbers.any { num ->
                    num.filter { it.isDigit() }.contains(queryDigits)
                }
                nameMatch || emailMatch || phoneMatch
            }
            .take(5)
            .toList()

        if (matched.isEmpty()) {
            return "No contacts found matching \"$query\"."
        }

        return matched.joinToString("\n\n") { c ->
            val phones = if (c.numbers.isNotEmpty()) "Phone: " + c.numbers.joinToString(", ") else "No phone"
            val emails = if (c.emails.isNotEmpty()) "Email: " + c.emails.joinToString(", ") else null
            "• ${c.name}\n  $phones" + (if (emails != null) "\n  $emails" else "")
        }
    }

    /**
     * Unified chronological schedule combining calendar events and due tasks.
     * [whenWindow] can be "today", "tomorrow", or "week".
     */
    fun querySchedule(whenWindow: String, now: Long = System.currentTimeMillis()): String {
        val today = LocalDayWindow.containing(now)
        val calendarGranted = provider.isCalendarPermissionGranted()

        val window = when (whenWindow.lowercase()) {
            "tomorrow" -> {
                val tomorrowStart = today.endsAtExclusive
                val tomorrowEnd = LocalDayWindow.containing(tomorrowStart).endsAtExclusive
                LocalDayWindow(tomorrowStart, tomorrowEnd)
            }
            "week" -> {
                val zone = TimeZone.getDefault()
                val days = CalendarWeekPlanner.days(now, zone)
                LocalDayWindow(days.first().startsAt, days.last().endsAtExclusive)
            }
            else -> today // "today"
        }

        val windowLabel = when (whenWindow.lowercase()) {
            "tomorrow" -> "Tomorrow"
            "week" -> "This week"
            else -> "Today"
        }

        val timeFormat = DateFormat.getTimeInstance(DateFormat.SHORT)
        val dateFormat = DateFormat.getDateInstance(DateFormat.MEDIUM)

        // Gather calendar events
        val calendarLines = mutableListOf<String>()
        if (calendarGranted) {
            val eventsInWindow = provider.calendarEvents().filter { it.startsAt >= window.startsAt && it.startsAt < window.endsAtExclusive }
            eventsInWindow.sortedBy { it.startsAt }.forEach { event ->
                val timeStr = if (event.allDay) "All day" else "${timeFormat.format(Date(event.startsAt))} - ${timeFormat.format(Date(event.endsAt))}"
                val locStr = if (event.location.isNotBlank()) " (${event.location})" else ""
                calendarLines.add("• $timeStr: ${event.title}$locStr")
            }
        }

        // Gather due tasks
        val openTasks = provider.tasks().filterNot { it.completed }
        val tasksInWindow = openTasks.filter { task ->
            task.dueAt != null && task.dueAt >= window.startsAt && task.dueAt < window.endsAtExclusive
        }.sortedBy { it.dueAt }

        val taskLines = tasksInWindow.map { task ->
            val dueStr = if (whenWindow.lowercase() == "week") " (${dateFormat.format(Date(task.dueAt!!))})" else ""
            val repStr = if (task.repeats) " [${task.recurrence.chipLabel}]" else ""
            "• ${task.title}$dueStr$repStr"
        }

        return buildString {
            append("$windowLabel's Schedule:\n")
            if (calendarGranted) {
                if (calendarLines.isEmpty()) {
                    append("Calendar: No events scheduled.\n")
                } else {
                    append("Calendar events (${calendarLines.size}):\n")
                    calendarLines.forEach { append(it).append("\n") }
                }
            } else {
                append("Calendar: Permission not granted to read phone calendar.\n")
            }

            if (taskLines.isEmpty()) {
                append("Tasks: No open tasks due $windowLabel.\n")
            } else {
                append("Tasks due ($windowLabel, ${taskLines.size}):\n")
                taskLines.forEach { append(it).append("\n") }
            }
        }.trimEnd()
    }
}
