package com.example.myjarvice.ui.main

import android.Manifest
import android.content.ContentUris
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.CalendarContract
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.core.content.ContextCompat
import com.example.myjarvice.data.CalendarAgendaItem
import com.example.myjarvice.data.CalendarAgendaRepository
import com.example.myjarvice.data.ChatSession
import com.example.myjarvice.data.RememberInboxStore
import com.example.myjarvice.data.RememberItem
import com.example.myjarvice.data.TodayBrief
import com.example.myjarvice.ui.JarvisBrandMark
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.text.DateFormat
import java.util.Calendar
import java.util.Date

/** Keeps the home surface independent of chat, attachment, and microphone state. */
@Composable
internal fun AssistantHome(
    store: RememberInboxStore,
    sessions: List<ChatSession>,
    refreshKey: Boolean,
    onOpenSaved: (String?) -> Unit,
    onOpenSession: (ChatSession) -> Unit,
    onOpenTools: () -> Unit,
    onDraft: () -> Unit
) {
    val context = LocalContext.current
    val calendarRepository = remember { CalendarAgendaRepository(context.applicationContext) }
    var entries by remember { mutableStateOf<List<RememberItem>>(emptyList()) }
    var calendarEvents by remember { mutableStateOf<List<CalendarAgendaItem>>(emptyList()) }
    var calendarGranted by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED)
    }
    var calendarLoading by remember { mutableStateOf(calendarGranted) }
    var calendarError by remember { mutableStateOf<String?>(null) }
    var loaded by remember { mutableStateOf(false) }
    var revision by remember { mutableIntStateOf(0) }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var showCapture by rememberSaveable { mutableStateOf(false) }
    val calendarPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        calendarGranted = granted
        calendarError = if (granted) null else "Calendar access wasn't granted. Today still works with saved reminders."
        if (granted) revision++
    }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) revision++
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(revision, refreshKey) {
        entries = withContext(Dispatchers.IO) { store.items() }
        now = System.currentTimeMillis()
        loaded = true
        calendarGranted = ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED
        if (calendarGranted) {
            calendarLoading = true
            calendarError = null
            try {
                calendarEvents = withContext(Dispatchers.IO) { calendarRepository.today(now) }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (_: Exception) {
                calendarError = "Calendar couldn't be read. Try opening Jarvis again."
            }
            calendarLoading = false
        } else {
            calendarEvents = emptyList()
            calendarLoading = false
        }
    }
    LaunchedEffect(Unit) {
        while (true) {
            delay(60_000)
            revision++
        }
    }
    if (showCapture) QuickCaptureDialog(store, onDismiss = { showCapture = false }, onSaved = {
        showCapture = false
        revision++
    })
    TodayHomeContent(
        brief = TodayBrief.from(entries, now), now = now, loaded = loaded,
        calendarGranted = calendarGranted, calendarEvents = calendarEvents,
        calendarLoading = calendarLoading, calendarError = calendarError,
        onRequestCalendar = { calendarPermission.launch(Manifest.permission.READ_CALENDAR) },
        onOpenCalendarEvent = { event ->
            val uri = ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, event.eventId)
            runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, uri).apply {
                putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, event.startsAt)
                putExtra(CalendarContract.EXTRA_EVENT_END_TIME, event.endsAt)
            }) }.onFailure { Toast.makeText(context, "No calendar app could open this event.", Toast.LENGTH_LONG).show() }
        },
        sessions = sessions.take(2), onOpenSaved = onOpenSaved, onOpenSession = onOpenSession,
        onCapture = { showCapture = true }, onOpenTools = onOpenTools, onDraft = onDraft
    )
}

@Composable
internal fun TodayHomeContent(
    brief: TodayBrief,
    now: Long,
    loaded: Boolean = true,
    calendarGranted: Boolean = false,
    calendarEvents: List<CalendarAgendaItem> = emptyList(),
    calendarLoading: Boolean = false,
    calendarError: String? = null,
    onRequestCalendar: () -> Unit = {},
    onOpenCalendarEvent: (CalendarAgendaItem) -> Unit = {},
    sessions: List<ChatSession> = emptyList(),
    onOpenSaved: (String?) -> Unit = {},
    onOpenSession: (ChatSession) -> Unit = {},
    onCapture: () -> Unit = {},
    onOpenTools: () -> Unit = {},
    onDraft: () -> Unit = {}
) {
    val colors = MaterialTheme.colorScheme
    val greeting = when (Calendar.getInstance().apply { timeInMillis = now }.get(Calendar.HOUR_OF_DAY)) {
        in 0..11 -> "Good morning"
        in 12..17 -> "Good afternoon"
        else -> "Good evening"
    }
    LazyColumn(
        modifier = Modifier.widthIn(max = 760.dp).fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Surface(shape = RoundedCornerShape(28.dp), color = colors.primaryContainer) {
                Column(Modifier.fillMaxWidth().background(Brush.linearGradient(listOf(
                    colors.primaryContainer, colors.tertiaryContainer.copy(alpha = 0.65f)
                ))).padding(22.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        JarvisBrandMark(Modifier.size(40.dp))
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text("YOUR SPACE", style = MaterialTheme.typography.labelSmall, color = colors.onPrimaryContainer)
                            Text(DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(now)),
                                style = MaterialTheme.typography.labelLarge, color = colors.onPrimaryContainer)
                        }
                    }
                    Spacer(Modifier.height(20.dp))
                    Text(greeting, style = MaterialTheme.typography.headlineMedium, color = colors.onPrimaryContainer)
                    Text("A clearer day starts here.", style = MaterialTheme.typography.bodyLarge,
                        color = colors.onPrimaryContainer, modifier = Modifier.padding(top = 6.dp))
                }
            }
        }
        item {
            HomeSectionTitle("Today", "Saved on your phone")
            Surface(shape = RoundedCornerShape(24.dp), color = colors.surfaceContainerLow) {
                Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(if (!loaded) "Opening your brief…" else when {
                        brief.overdue.isNotEmpty() -> "${brief.overdue.size} past-due ${if (brief.overdue.size == 1) "reminder" else "reminders"} to review"
                        brief.remindersToday.isNotEmpty() -> "${brief.remindersToday.size} ${if (brief.remindersToday.size == 1) "reminder" else "reminders"} ahead today"
                        else -> "Room to focus"
                    }, style = MaterialTheme.typography.titleMedium)
                    Text(if (!loaded) "" else if (brief.remindersToday.isEmpty() && brief.overdue.isEmpty())
                        "No pending saved reminders today. Capture a thought or pick up where you left off."
                    else "Your saved reminders, in time order. Open one to review or reschedule it.",
                        style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
                    (brief.overdue + brief.remindersToday).take(3).forEach { item ->
                        HomeLink(item.title, if (item.reminderAt!! < now) "Past due · ${formatReminder(item.reminderAt)}"
                            else "Today · ${DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(item.reminderAt))}",
                            onClick = { onOpenSaved(item.id) })
                    }
                    if (brief.overdue.isEmpty() && brief.remindersToday.isEmpty()) brief.nextReminder?.let { item ->
                        HomeLink(item.title, "Next · ${formatReminder(item.reminderAt!!)}", onClick = { onOpenSaved(item.id) })
                    }
                    TextButton(onClick = { onOpenSaved(null) }) { Text("Open saved inbox · ${brief.savedCount}") }
                }
            }
        }
        item {
            HomeSectionTitle("Your calendar", "Read privately from this phone")
            Surface(shape = RoundedCornerShape(24.dp), color = colors.surfaceContainerLow) {
                Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    when {
                        !calendarGranted -> {
                            Text("See today's schedule", style = MaterialTheme.typography.titleMedium)
                            Text("Grant read-only access to show events here. Jarvis won't edit your calendar or send event details to a model.",
                                style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
                            Button(onClick = onRequestCalendar) { Text("Connect calendar") }
                        }
                        calendarLoading -> {
                            Text("Reading today's schedule…", style = MaterialTheme.typography.titleMedium)
                            LinearProgressIndicator(Modifier.fillMaxWidth())
                        }
                        calendarError != null -> {
                            Text("Calendar unavailable", style = MaterialTheme.typography.titleMedium)
                            Text(calendarError.orEmpty(), style = MaterialTheme.typography.bodyMedium, color = colors.error)
                        }
                        calendarEvents.isEmpty() -> {
                            Text("No events today", style = MaterialTheme.typography.titleMedium)
                            Text("Your calendar is clear. Saved reminders still appear above.",
                                style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
                        }
                        else -> {
                            val upcoming = calendarEvents.firstOrNull { it.endsAt >= now } ?: calendarEvents.last()
                            Text(if (upcoming.endsAt >= now) "Next on your calendar" else "Today's calendar",
                                style = MaterialTheme.typography.titleMedium)
                            calendarEvents.take(5).forEach { event ->
                                HomeLink(event.title, calendarEventSubtitle(event), onClick = { onOpenCalendarEvent(event) })
                            }
                            if (calendarEvents.size > 5) Text("${calendarEvents.size - 5} more events in your calendar",
                                style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
                        }
                    }
                }
            }
        }
        item {
            HomeSectionTitle("Make a little progress", "Start with one thing")
            HomeLink("Capture a thought", "Save a note or link instantly, even offline", onClick = onCapture)
            Spacer(Modifier.height(8.dp))
            HomeLink("Find the right words", "Prepare a message with your choice of tone", onClick = onDraft)
            Spacer(Modifier.height(8.dp))
            HomeLink("Explore shortcuts", "Search phone tools, memory and PC actions", onClick = onOpenTools)
        }
        if (sessions.isNotEmpty()) {
            item { HomeSectionTitle("Pick up where you left off", "Recent conversations") }
            items(sessions, key = { "chat:${it.id}" }) { session ->
                HomeLink(session.title, DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(session.updatedAt)),
                    onClick = { onOpenSession(session) })
            }
        }
        if (brief.recentItems.isNotEmpty()) {
            item { HomeSectionTitle("Fresh in your inbox", "Notes, links and things to revisit") }
            items(brief.recentItems, key = { "saved:${it.id}" }) { item ->
                HomeLink(item.title, item.summary, onClick = { onOpenSaved(item.id) })
            }
        }
        item {
            Text("Today uses local saved items${if (calendarGranted) " and read-only calendar events" else ""}. PC tasks aren't included.",
                color = colors.onSurfaceVariant, style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(vertical = 8.dp))
        }
    }
}

@Composable
private fun HomeSectionTitle(title: String, subtitle: String) {
    Column(Modifier.padding(top = 8.dp, bottom = 10.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun HomeLink(title: String, subtitle: String, onClick: () -> Unit) {
    Surface(onClick = onClick, shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surfaceContainer) {
        Row(Modifier.fillMaxWidth().heightIn(min = 64.dp).padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (subtitle.isNotBlank()) Text(subtitle, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 4.dp))
            }
            Text("›", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(start = 12.dp))
        }
    }
}

@Composable
private fun QuickCaptureDialog(store: RememberInboxStore, onDismiss: () -> Unit, onSaved: () -> Unit) {
    var note by rememberSaveable { mutableStateOf("") }
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(saving) {
        if (saving) {
            try {
                withContext(Dispatchers.IO) { store.addText(note) }
                onSaved()
            } catch (e: kotlinx.coroutines.CancellationException) { throw e
            } catch (e: Exception) { error = "Couldn't save your note. Please try again." }
            finally { saving = false }
        }
    }
    AlertDialog(onDismissRequest = { if (!saving) onDismiss() },
        title = { Text("Keep this thought") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text("Saved privately on this phone. Add a reminder from your saved inbox.", style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(note, { note = it.take(4000) }, enabled = !saving, label = { Text("Note or link") },
                    minLines = 3, maxLines = 6, modifier = Modifier.fillMaxWidth(), supportingText = { Text("${note.length}/4,000") })
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = { TextButton(onClick = { saving = true }, enabled = note.isNotBlank() && !saving) { Text(if (saving) "Saving…" else "Save note") } },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !saving) { Text("Cancel") } })
}

private fun formatReminder(time: Long) = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(time))

private fun calendarEventSubtitle(event: CalendarAgendaItem): String = buildString {
    append(if (event.allDay) "All day" else {
        val start = DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(event.startsAt))
        val end = DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(event.endsAt))
        "$start–$end"
    })
    if (event.location.isNotBlank()) append(" · ${event.location}")
}
