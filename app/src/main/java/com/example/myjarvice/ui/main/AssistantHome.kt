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
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.BookmarkBorder
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.EditNote
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Inbox
import androidx.compose.material.icons.rounded.Lightbulb
import androidx.compose.material.icons.rounded.NotificationsNone
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
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
import com.example.myjarvice.data.LocalTask
import com.example.myjarvice.data.LocalTaskStore
import com.example.myjarvice.data.LocalDayWindow
import com.example.myjarvice.data.TaskAgenda
import com.example.myjarvice.data.TodayBrief
import com.example.myjarvice.ui.JarvisBrandMark
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
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
    val taskStore = remember { LocalTaskStore(context.applicationContext) }
    val scope = rememberCoroutineScope()
    var entries by remember { mutableStateOf<List<RememberItem>>(emptyList()) }
    var tasks by remember { mutableStateOf<List<LocalTask>>(emptyList()) }
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
    var showWeekPlanner by rememberSaveable { mutableStateOf(false) }
    var showTasks by rememberSaveable { mutableStateOf(false) }
    var showTaskEditor by rememberSaveable { mutableStateOf(false) }
    var editingTask by remember { mutableStateOf<LocalTask?>(null) }
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
        tasks = withContext(Dispatchers.IO) { taskStore.tasks() }
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
    if (showWeekPlanner) CalendarWeekDialog(onDismiss = { showWeekPlanner = false; revision++ })
    if (showTasks) TaskListSheet(
        agenda = TaskAgenda.from(tasks, now),
        now = now,
        onAdd = { editingTask = null; showTaskEditor = true },
        onEdit = { editingTask = it; showTaskEditor = true },
        onToggle = { task, complete -> scope.launch {
            try {
                withContext(Dispatchers.IO) { taskStore.setCompleted(task.id, complete) }
                revision++
            } catch (error: kotlinx.coroutines.CancellationException) { throw error
            } catch (_: Exception) { Toast.makeText(context, "Couldn't update this task.", Toast.LENGTH_LONG).show() }
        } },
        onDismiss = { showTasks = false }
    )
    if (showTaskEditor) TaskEditorDialog(
        task = editingTask,
        now = now,
        onDismiss = { showTaskEditor = false },
        onSave = { title, notes, dueAt -> scope.launch {
            try {
                withContext(Dispatchers.IO) { taskStore.saveTask(editingTask?.id, title, notes, dueAt) }
                showTaskEditor = false
                revision++
            } catch (error: kotlinx.coroutines.CancellationException) { throw error
            } catch (error: Exception) { Toast.makeText(context, error.message ?: "Couldn't save this task.", Toast.LENGTH_LONG).show() }
        } },
        onDelete = editingTask?.let { task -> { scope.launch {
            try {
                withContext(Dispatchers.IO) { taskStore.delete(task.id) }
                showTaskEditor = false
                revision++
            } catch (error: kotlinx.coroutines.CancellationException) { throw error
            } catch (_: Exception) { Toast.makeText(context, "Couldn't delete this task.", Toast.LENGTH_LONG).show() }
        } } }
    )
    TodayHomeContent(
        brief = TodayBrief.from(entries, now), now = now, loaded = loaded,
        taskAgenda = TaskAgenda.from(tasks, now),
        calendarGranted = calendarGranted, calendarEvents = calendarEvents,
        calendarLoading = calendarLoading, calendarError = calendarError,
        onOpenWeekPlanner = { showWeekPlanner = true },
        onRequestCalendar = { calendarPermission.launch(Manifest.permission.READ_CALENDAR) },
        onOpenCalendarEvent = { event ->
            val uri = ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, event.eventId)
            runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, uri).apply {
                putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, event.startsAt)
                putExtra(CalendarContract.EXTRA_EVENT_END_TIME, event.endsAt)
            }) }.onFailure { Toast.makeText(context, "No calendar app could open this event.", Toast.LENGTH_LONG).show() }
        },
        sessions = sessions.take(2), onOpenSaved = onOpenSaved, onOpenSession = onOpenSession,
        onCapture = { showCapture = true }, onOpenTools = onOpenTools, onDraft = onDraft,
        onOpenTasks = { showTasks = true },
        onAddTask = { editingTask = null; showTaskEditor = true },
        onEditTask = { editingTask = it; showTaskEditor = true },
        onToggleTask = { task, complete -> scope.launch {
            try {
                withContext(Dispatchers.IO) { taskStore.setCompleted(task.id, complete) }
                revision++
            } catch (error: kotlinx.coroutines.CancellationException) { throw error
            } catch (_: Exception) { Toast.makeText(context, "Couldn't update this task.", Toast.LENGTH_LONG).show() }
        } }
    )
}

@Composable
internal fun TodayHomeContent(
    brief: TodayBrief,
    now: Long,
    taskAgenda: TaskAgenda = TaskAgenda.from(emptyList(), now),
    loaded: Boolean = true,
    calendarGranted: Boolean = false,
    calendarEvents: List<CalendarAgendaItem> = emptyList(),
    calendarLoading: Boolean = false,
    calendarError: String? = null,
    onRequestCalendar: () -> Unit = {},
    onOpenCalendarEvent: (CalendarAgendaItem) -> Unit = {},
    onOpenWeekPlanner: () -> Unit = {},
    sessions: List<ChatSession> = emptyList(),
    onOpenSaved: (String?) -> Unit = {},
    onOpenSession: (ChatSession) -> Unit = {},
    onCapture: () -> Unit = {},
    onOpenTools: () -> Unit = {},
    onDraft: () -> Unit = {},
    onOpenTasks: () -> Unit = {},
    onAddTask: () -> Unit = {},
    onEditTask: (LocalTask) -> Unit = {},
    onToggleTask: (LocalTask, Boolean) -> Unit = { _, _ -> }
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
            Surface(shape = RoundedCornerShape(28.dp), color = colors.primaryContainer, tonalElevation = 2.dp) {
                Column(Modifier.fillMaxWidth().background(Brush.linearGradient(listOf(
                    colors.primaryContainer, colors.tertiaryContainer.copy(alpha = 0.5f)
                ))).padding(22.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        JarvisBrandMark(Modifier.size(38.dp))
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text("TODAY", style = MaterialTheme.typography.labelSmall, color = colors.onPrimaryContainer)
                            Text(DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(now)),
                                style = MaterialTheme.typography.labelLarge, color = colors.onPrimaryContainer)
                        }
                    }
                    Spacer(Modifier.height(18.dp))
                    Text(greeting, style = MaterialTheme.typography.headlineMedium, color = colors.onPrimaryContainer)
                    Text("Here’s what needs your attention.", style = MaterialTheme.typography.bodyLarge,
                        color = colors.onPrimaryContainer, modifier = Modifier.padding(top = 6.dp))
                    Row(Modifier.fillMaxWidth().padding(top = 18.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        HomeMetric("Open", taskAgenda.open.size.toString(), Modifier.weight(1f))
                        HomeMetric("Today", (taskAgenda.dueToday.size + brief.remindersToday.size).toString(), Modifier.weight(1f))
                        HomeMetric("Saved", brief.savedCount.toString(), Modifier.weight(1f))
                    }
                }
            }
        }
        item {
            HomeSectionTitle("Quick actions", "Common things, one tap away", Icons.Rounded.Lightbulb)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                HomeQuickAction("Task", Icons.Rounded.Add, onAddTask, Modifier.weight(1f))
                HomeQuickAction("Capture", Icons.Rounded.Inbox, onCapture, Modifier.weight(1f))
                HomeQuickAction("Draft", Icons.Rounded.EditNote, onDraft, Modifier.weight(1f))
                HomeQuickAction("Tools", Icons.Rounded.GridView, onOpenTools, Modifier.weight(1f))
            }
        }
        item {
            HomeSectionTitle("Your tasks", "Private · saved on this phone", Icons.Rounded.CheckCircle,
                action = if (taskAgenda.open.isNotEmpty() || taskAgenda.completed.isNotEmpty()) "View all" else null,
                onAction = onOpenTasks)
            Surface(shape = RoundedCornerShape(24.dp), color = colors.surfaceContainerLow, tonalElevation = 1.dp) {
                Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(when {
                        taskAgenda.overdue.isNotEmpty() -> "${taskAgenda.overdue.size} overdue · ${taskAgenda.open.size} open"
                        taskAgenda.dueToday.isNotEmpty() -> "${taskAgenda.dueToday.size} due today · ${taskAgenda.open.size} open"
                        taskAgenda.open.isNotEmpty() -> "${taskAgenda.open.size} open ${if (taskAgenda.open.size == 1) "task" else "tasks"}"
                        else -> "Your list is clear"
                    }, style = MaterialTheme.typography.titleMedium)
                    if (taskAgenda.open.isEmpty()) Text("Add one concrete next step. It stays available offline.",
                        style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
                    taskAgenda.open.take(4).forEach { task ->
                        HomeTaskRow(task, now, onToggle = { onToggleTask(task, it) }, onEdit = { onEditTask(task) })
                    }
                    FilledTonalButton(onClick = onAddTask) {
                        Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Add task")
                    }
                }
            }
        }
        item {
            HomeSectionTitle("Saved reminders", "Notes and links with a time", Icons.Rounded.NotificationsNone)
            Surface(shape = RoundedCornerShape(24.dp), color = colors.surfaceContainerLow, tonalElevation = 1.dp) {
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
                            icon = Icons.Rounded.BookmarkBorder, onClick = { onOpenSaved(item.id) })
                    }
                    if (brief.overdue.isEmpty() && brief.remindersToday.isEmpty()) brief.nextReminder?.let { item ->
                        HomeLink(item.title, "Next · ${formatReminder(item.reminderAt!!)}", Icons.Rounded.BookmarkBorder,
                            onClick = { onOpenSaved(item.id) })
                    }
                    TextButton(onClick = { onOpenSaved(null) }) { Text("Open saved inbox · ${brief.savedCount}") }
                }
            }
        }
        item {
            HomeSectionTitle("Your calendar", "Read privately from this phone", Icons.Rounded.CalendarMonth)
            Surface(shape = RoundedCornerShape(24.dp), color = colors.surfaceContainerLow, tonalElevation = 1.dp) {
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
                                HomeLink(event.title, calendarEventSubtitle(event), Icons.Rounded.CalendarMonth,
                                    onClick = { onOpenCalendarEvent(event) })
                            }
                            if (calendarEvents.size > 5) Text("${calendarEvents.size - 5} more events in your calendar",
                                style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
                        }
                    }
                    OutlinedButton(onClick = onOpenWeekPlanner, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                        Icon(Icons.Rounded.CalendarMonth, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Plan my week")
                    }
                }
            }
        }
        item {
            HomeSectionTitle("Make a little progress", "Start with one thing", Icons.Rounded.Lightbulb)
            HomeLink("Capture a thought", "Save a note or link instantly, even offline", Icons.Rounded.Inbox, onClick = onCapture)
            Spacer(Modifier.height(8.dp))
            HomeLink("Find the right words", "Prepare a message with your choice of tone", Icons.Rounded.EditNote, onClick = onDraft)
            Spacer(Modifier.height(8.dp))
            HomeLink("Explore shortcuts", "Search phone tools, memory and PC actions", Icons.Rounded.GridView, onClick = onOpenTools)
        }
        if (sessions.isNotEmpty()) {
            item { HomeSectionTitle("Pick up where you left off", "Recent conversations", Icons.Rounded.History) }
            items(sessions, key = { "chat:${it.id}" }) { session ->
                HomeLink(session.title, DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(session.updatedAt)), Icons.Rounded.History,
                    onClick = { onOpenSession(session) })
            }
        }
        if (brief.recentItems.isNotEmpty()) {
            item { HomeSectionTitle("Fresh in your inbox", "Notes, links and things to revisit", Icons.Rounded.Inbox) }
            items(brief.recentItems, key = { "saved:${it.id}" }) { item ->
                HomeLink(item.title, item.summary, Icons.Rounded.BookmarkBorder, onClick = { onOpenSaved(item.id) })
            }
        }
        item {
            Text("Today uses phone tasks, local saved items${if (calendarGranted) " and read-only calendar events" else ""}. PC tasks stay separate.",
                color = colors.onSurfaceVariant, style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(vertical = 8.dp))
        }
    }
}

@Composable
private fun HomeSectionTitle(title: String, subtitle: String, icon: androidx.compose.ui.graphics.vector.ImageVector,
    action: String? = null, onAction: () -> Unit = {}) {
    Row(Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        com.example.myjarvice.ui.JarvisIconBadge(icon, modifier = Modifier.size(36.dp))
        Column(Modifier.weight(1f).padding(start = 12.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (action != null) TextButton(onClick = onAction) { Text(action) }
    }
}

@Composable
private fun HomeLink(title: String, subtitle: String, icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit) {
    Surface(onClick = onClick, shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surfaceContainer,
        tonalElevation = 1.dp) {
        Row(Modifier.fillMaxWidth().heightIn(min = 64.dp).padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            com.example.myjarvice.ui.JarvisIconBadge(icon, modifier = Modifier.size(38.dp),
                containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                contentColor = MaterialTheme.colorScheme.primary)
            Column(Modifier.weight(1f).padding(start = 14.dp)) {
                Text(title, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (subtitle.isNotBlank()) Text(subtitle, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 4.dp))
            }
            Icon(Icons.AutoMirrored.Rounded.ArrowForward, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 12.dp).size(18.dp))
        }
    }
}

@Composable
private fun HomeMetric(label: String, value: String, modifier: Modifier = Modifier) {
    Surface(modifier = modifier, shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surface.copy(alpha = 0.2f)) {
        Column(Modifier.padding(horizontal = 10.dp, vertical = 9.dp)) {
            Text(value, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f))
        }
    }
}

@Composable
private fun HomeQuickAction(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit, modifier: Modifier = Modifier) {
    Surface(onClick = onClick, modifier = modifier.heightIn(min = 78.dp), shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainer, tonalElevation = 1.dp) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
            Text(label, style = MaterialTheme.typography.labelSmall, maxLines = 1,
                modifier = Modifier.padding(top = 7.dp))
        }
    }
}

@Composable
private fun HomeTaskRow(task: LocalTask, now: Long, onToggle: (Boolean) -> Unit, onEdit: () -> Unit) {
    Surface(shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.surfaceContainer) {
        Row(Modifier.fillMaxWidth().heightIn(min = 62.dp).padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = task.completed, onCheckedChange = onToggle,
                modifier = Modifier.semantics { contentDescription = if (task.completed) "Reopen task: ${task.title}" else "Complete task: ${task.title}" })
            Column(Modifier.weight(1f).padding(horizontal = 6.dp)) {
                Text(task.title, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                taskDueLabel(task, now)?.let { label ->
                    Text(label, style = MaterialTheme.typography.bodySmall,
                        color = if (label.startsWith("Overdue")) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            IconButton(onClick = onEdit, modifier = Modifier.semantics { contentDescription = "Edit task: ${task.title}" }) {
                Icon(Icons.Rounded.Edit, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TaskListSheet(
    agenda: TaskAgenda,
    now: Long,
    onAdd: () -> Unit,
    onEdit: (LocalTask) -> Unit,
    onToggle: (LocalTask, Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.fillMaxWidth().heightIn(max = 650.dp).verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp).padding(bottom = 28.dp)) {
            Text("Your tasks", style = MaterialTheme.typography.headlineSmall)
            Text("Stored privately on this phone. PC tasks remain separate.", style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp, bottom = 14.dp))
            Button(onClick = onAdd, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Add a task")
            }
            if (agenda.open.isEmpty()) {
                Text("Nothing open right now.", style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(vertical = 24.dp))
            } else {
                Text("Open · ${agenda.open.size}", style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.padding(top = 20.dp, bottom = 8.dp))
                agenda.open.forEach { task ->
                    HomeTaskRow(task, now, onToggle = { onToggle(task, it) }, onEdit = { onEdit(task) })
                    Spacer(Modifier.height(8.dp))
                }
            }
            if (agenda.completed.isNotEmpty()) {
                HorizontalDivider(Modifier.padding(vertical = 14.dp))
                Text("Recently completed", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(bottom = 8.dp))
                agenda.completed.forEach { task ->
                    HomeTaskRow(task, now, onToggle = { onToggle(task, it) }, onEdit = { onEdit(task) })
                    Spacer(Modifier.height(8.dp))
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TaskEditorDialog(
    task: LocalTask?,
    now: Long,
    onDismiss: () -> Unit,
    onSave: (String, String, Long?) -> Unit,
    onDelete: (() -> Unit)?
) {
    var title by rememberSaveable(task?.id) { mutableStateOf(task?.title.orEmpty()) }
    var notes by rememberSaveable(task?.id) { mutableStateOf(task?.notes.orEmpty()) }
    var dueAt by rememberSaveable(task?.id) { mutableStateOf(task?.dueAt) }
    var showDatePicker by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    val today = LocalDayWindow.containing(now)
    val datePickerState = rememberDatePickerState(initialSelectedDateMillis = dueAt)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (task == null) "Add a task" else "Edit task") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text("This task stays on your phone and works offline.", style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                OutlinedTextField(title, { title = it.take(180) }, label = { Text("Task") }, singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(top = 14.dp), supportingText = { Text("${title.length}/180") })
                OutlinedTextField(notes, { notes = it.take(2000) }, label = { Text("Notes (optional)") },
                    minLines = 2, maxLines = 4, modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
                Text("Due date", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 16.dp, bottom = 6.dp))
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = dueAt == null, onClick = { dueAt = null }, label = { Text("No date") })
                    FilterChip(selected = dueAt == today.startsAt, onClick = { dueAt = today.startsAt }, label = { Text("Today") })
                    FilterChip(selected = dueAt == today.endsAtExclusive, onClick = { dueAt = today.endsAtExclusive }, label = { Text("Tomorrow") })
                    FilterChip(selected = dueAt != null && dueAt != today.startsAt && dueAt != today.endsAtExclusive,
                        onClick = { showDatePicker = true }, label = { Text(dueAt?.let(::formatTaskDate) ?: "Pick date") })
                }
                if (onDelete != null) {
                    TextButton(onClick = { confirmDelete = true }, modifier = Modifier.padding(top = 12.dp)) {
                        Text("Delete task", color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onSave(title, notes, dueAt) }, enabled = title.isNotBlank()) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
    if (showDatePicker) DatePickerDialog(
        onDismissRequest = { showDatePicker = false },
        confirmButton = { TextButton(onClick = {
            datePickerState.selectedDateMillis?.let { dueAt = pickerDateToLocalStart(it) }
            showDatePicker = false
        }) { Text("Use date") } },
        dismissButton = { TextButton(onClick = { showDatePicker = false }) { Text("Cancel") } }
    ) { DatePicker(state = datePickerState) }
    if (confirmDelete && onDelete != null) AlertDialog(
        onDismissRequest = { confirmDelete = false },
        title = { Text("Delete this task?") },
        text = { Text("This removes the task from this phone. This can't be undone from the task list.") },
        confirmButton = { TextButton(onClick = onDelete) { Text("Delete", color = MaterialTheme.colorScheme.error) } },
        dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Keep task") } }
    )
}

private fun taskDueLabel(task: LocalTask, now: Long): String? {
    if (task.completed) return "Completed"
    val due = task.dueAt ?: return null
    val today = LocalDayWindow.containing(now)
    return when {
        due < today.startsAt -> "Overdue · ${formatTaskDate(due)}"
        due < today.endsAtExclusive -> "Due today"
        due < LocalDayWindow.containing(today.endsAtExclusive).endsAtExclusive -> "Due tomorrow"
        else -> "Due ${formatTaskDate(due)}"
    }
}

private fun formatTaskDate(time: Long): String = DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(time))

private fun pickerDateToLocalStart(utcDate: Long): Long {
    val utc = Calendar.getInstance(java.util.TimeZone.getTimeZone("UTC")).apply { timeInMillis = utcDate }
    return Calendar.getInstance().apply {
        clear()
        set(utc.get(Calendar.YEAR), utc.get(Calendar.MONTH), utc.get(Calendar.DAY_OF_MONTH))
    }.timeInMillis
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
