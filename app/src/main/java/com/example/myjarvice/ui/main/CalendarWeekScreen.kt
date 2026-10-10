package com.example.myjarvice.ui.main

import android.Manifest
import android.content.ContentUris
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.CalendarContract
import android.provider.Settings
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.myjarvice.data.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.DateFormat
import java.util.Date

/** Visible, read-only planning. No WRITE_CALENDAR permission, model call, event cache or background job. */
@Composable
internal fun CalendarWeekDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val repository = remember { CalendarAgendaRepository(context.applicationContext) }
    var revision by remember { mutableIntStateOf(0) }
    var granted by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var snapshot by remember { mutableStateOf<CalendarSnapshot?>(null) }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var duration by rememberSaveable { mutableIntStateOf(60) }
    var days by remember { mutableStateOf(emptyList<CalendarPlanDay>()) }
    var planning by remember { mutableStateOf(false) }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { revision++ }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_RESUME) revision++ }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    fun hasPermission() = ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED
    LaunchedEffect(revision) {
        val attempt = revision
        busy = true; error = null; snapshot = null; days = emptyList()
        granted = hasPermission()
        try {
            if (granted) {
                val checkedAt = System.currentTimeMillis()
                val result = withContext(Dispatchers.IO) { repository.week(checkedAt) }
                granted = hasPermission()
                if (granted) { now = checkedAt; snapshot = result }
            }
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) {
            granted = hasPermission()
            error = if (granted) "Calendar could not be read. Refresh or check your calendar app." else null
        } finally { if (attempt == revision) busy = false }
    }
    LaunchedEffect(snapshot, duration, now) {
        val source = snapshot
        val minutes = duration
        val checkedAt = now
        days = emptyList(); planning = true
        try {
            source?.let { data -> days = withContext(Dispatchers.Default) { CalendarWeekPlanner.plan(data, checkedAt, minutes) } }
        } finally { if (source === snapshot && minutes == duration && checkedAt == now) planning = false }
    }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        CalendarWeekScreen(days, granted, busy || planning, snapshot != null, snapshot?.incomplete == true, error,
            now, duration, onDismiss, onRefresh = { revision++ },
            onRequestAccess = { permission.launch(Manifest.permission.READ_CALENDAR) },
            onOpenPermissions = {
                runCatching { context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.parse("package:${context.packageName}"))) }
            }, onDuration = { duration = it }, onOpenEvent = { event ->
                // Permission may have been revoked since the snapshot; never use stale access silently.
                if (!hasPermission()) { revision++; return@CalendarWeekScreen }
                val uri = ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, event.eventId)
                runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, uri).apply {
                    putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, event.startsAt)
                    putExtra(CalendarContract.EXTRA_EVENT_END_TIME, event.endsAt)
                }) }.onFailure { Toast.makeText(context, "No calendar app could open this event.", Toast.LENGTH_LONG).show() }
            })
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
internal fun CalendarWeekScreen(
    days: List<CalendarPlanDay>, granted: Boolean, busy: Boolean, readable: Boolean, incomplete: Boolean,
    error: String?, refreshedAt: Long, duration: Int,
    onDismiss: () -> Unit, onRefresh: () -> Unit, onRequestAccess: () -> Unit, onOpenPermissions: () -> Unit,
    onDuration: (Int) -> Unit, onOpenEvent: (CalendarAgendaItem) -> Unit
) {
    var expandedDays by remember { mutableStateOf(emptySet<Long>()) }
    val colors = MaterialTheme.colorScheme
    val time = DateFormat.getTimeInstance(DateFormat.SHORT)
    Scaffold(topBar = {
        TopAppBar(title = { Text("Plan my week", maxLines = 1, overflow = TextOverflow.Ellipsis) },
            navigationIcon = { IconButton(onClick = onDismiss) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Close week planner") } },
            actions = { IconButton(onClick = onRefresh, enabled = !busy) { Icon(Icons.Rounded.Refresh, "Refresh week planner") } })
    }) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding).padding(horizontal = 20.dp),
            contentPadding = PaddingValues(bottom = 28.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item {
                Text("Make room for what matters", style = MaterialTheme.typography.headlineSmall)
                Text("Next seven days · read-only · no AI or PC needed", style = MaterialTheme.typography.bodySmall,
                    color = colors.onSurfaceVariant, modifier = Modifier.padding(top = 6.dp))
            }
            if (busy) item { LinearProgressIndicator(Modifier.fillMaxWidth()); Text("Reading your week…") }
            if (!granted && !busy) item {
                OutlinedCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("Connect your calendar", style = MaterialTheme.typography.titleMedium)
                        Text("Jarvis reads visible event times and titles from this phone. It does not edit events, upload them or include them in model prompts.")
                        Button(onClick = onRequestAccess, modifier = Modifier.fillMaxWidth()) { Text("Allow read-only access") }
                        TextButton(onClick = onOpenPermissions) { Text("Open permission settings") }
                    }
                }
            }
            error?.let { message -> item { Text(message, color = colors.error) } }
            if (granted && readable && !busy) {
                item {
                    Text("Find an opening", style = MaterialTheme.typography.titleMedium)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        CalendarWeekPlanner.durations.forEach { minutes ->
                            FilterChip(selected = duration == minutes, onClick = { onDuration(minutes) }, label = { Text("$minutes min") })
                        }
                    }
                    Text("Suggestions use 9 am–6 pm, local time. Busy/tentative events block time; events marked free do not. All-day busy events block the day.",
                        style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
                    Text("Only visible calendars available on this phone are checked—not tasks, travel or unsynced calendars. No time is reserved. Refresh before relying on a suggestion.",
                        style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant, modifier = Modifier.padding(top = 6.dp))
                    Text("Checked ${time.format(Date(refreshedAt))}", style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(top = 6.dp))
                }
                if (incomplete) item {
                    Text("Calendar data is incomplete or exceeds the safety limit. Openings are hidden to avoid suggesting occupied time.",
                        color = colors.error, style = MaterialTheme.typography.bodyMedium)
                }
                days.forEach { day ->
                    val dayId = day.window.startsAt
                    item(key = "day:$dayId") {
                        HorizontalDivider(Modifier.padding(top = 12.dp, bottom = 8.dp))
                        Text(DateFormat.getDateInstance(DateFormat.FULL).format(Date(dayId)), style = MaterialTheme.typography.titleMedium)
                        Text("${day.events.size} events${if (day.conflicts.isNotEmpty()) " · ${day.conflicts.size} overlapping" else ""}",
                            style = MaterialTheme.typography.labelMedium, color = colors.onSurfaceVariant)
                    }
                    val visible = if (dayId in expandedDays) day.events else day.events.take(8)
                    items(visible, key = { "event:$dayId:${it.eventId}:${it.startsAt}:${it.endsAt}" }) { event ->
                        Surface(onClick = { onOpenEvent(event) }, modifier = Modifier.fillMaxWidth(),
                            shape = MaterialTheme.shapes.medium, color = colors.surfaceContainerLow) {
                            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(event.title, style = MaterialTheme.typography.titleSmall, maxLines = 3, overflow = TextOverflow.Ellipsis)
                                Text(when {
                                    event.allDay -> "All day"
                                    event.startsAt < day.window.startsAt && event.endsAt > day.window.endsAtExclusive -> "Continues through this day"
                                    event.startsAt < day.window.startsAt -> "Continues from yesterday · until ${time.format(Date(event.endsAt))}"
                                    event.endsAt > day.window.endsAtExclusive -> "${time.format(Date(event.startsAt))} · continues tomorrow"
                                    else -> "${time.format(Date(event.startsAt))} – ${time.format(Date(event.endsAt))}"
                                },
                                    style = MaterialTheme.typography.bodySmall)
                                if (event.location.isNotBlank()) Text(event.location, style = MaterialTheme.typography.bodySmall,
                                    color = colors.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                if (!event.blocksTime) Text("Marked as free time", style = MaterialTheme.typography.labelSmall)
                                if (event in day.conflicts) Text("Overlaps another busy event", color = colors.error, style = MaterialTheme.typography.labelMedium)
                            }
                        }
                    }
                    if (day.events.size > 8) item(key = "more:$dayId") {
                        TextButton(onClick = { expandedDays = if (dayId in expandedDays) expandedDays - dayId else expandedDays + dayId }) {
                            Text(if (dayId in expandedDays) "Show fewer events" else "Show ${day.events.size - 8} more events")
                        }
                    }
                    item(key = "openings:$dayId") {
                        OutlinedCard(Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Icon(Icons.Rounded.Schedule, null, tint = colors.primary)
                                Text("Suggested openings", style = MaterialTheme.typography.titleSmall)
                                if (day.openings.isEmpty()) Text(if (incomplete) "Unavailable with incomplete calendar data."
                                    else "No $duration-minute opening found in the remaining daytime window.", style = MaterialTheme.typography.bodySmall)
                                day.openings.forEach { opening -> Text("${time.format(Date(opening.startsAt))} – ${time.format(Date(opening.endsAt))}",
                                    style = MaterialTheme.typography.bodyMedium) }
                            }
                        }
                    }
                }
            }
        }
    }
}
