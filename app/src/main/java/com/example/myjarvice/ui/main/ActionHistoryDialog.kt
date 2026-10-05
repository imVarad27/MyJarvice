package com.example.myjarvice.ui.main

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.myjarvice.data.*
import com.example.myjarvice.ui.JarvisIconBadge
import java.text.DateFormat
import java.util.Date

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ActionHistoryDialog(
    events: List<ActionAuditEvent>,
    loading: Boolean,
    error: String?,
    phoneError: String? = null,
    onDismiss: () -> Unit,
    onRefresh: () -> Unit,
    onClearPhone: () -> Unit = {}
) {
    var query by rememberSaveable { mutableStateOf("") }
    var source by rememberSaveable { mutableStateOf(ActivitySource.ALL) }
    var outcome by rememberSaveable { mutableStateOf(ActivityOutcome.ALL) }
    var outcomeMenu by remember { mutableStateOf(false) }
    var confirmClear by remember { mutableStateOf(false) }
    val visible = remember(events, query, source, outcome) { ActionTimeline.visible(events, query, source, outcome) }
    val colors = MaterialTheme.colorScheme

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize(), color = colors.background) {
            Scaffold(containerColor = colors.background, topBar = {
                TopAppBar(title = { Text("Activity") }, navigationIcon = {
                    IconButton(onClick = onDismiss) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Close activity") }
                }, actions = {
                    IconButton(onClick = onRefresh, enabled = !loading) { Icon(Icons.Rounded.Refresh, "Refresh PC activity") }
                    IconButton(onClick = { confirmClear = true }, enabled = events.any { it.source == "phone" } || phoneError != null) {
                        Icon(Icons.Rounded.DeleteOutline, "Clear phone activity log")
                    }
                })
            }) { padding ->
                LazyColumn(Modifier.fillMaxSize().padding(padding).imePadding().padding(horizontal = 20.dp),
                    contentPadding = PaddingValues(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    item { Column {
                    Text("See what Jarvis did, prepared, or blocked.", style = MaterialTheme.typography.bodyLarge)
                    Text("Phone history stays private and works offline. PC history is fetched from your paired host.",
                        style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp, bottom = 14.dp))
                    OutlinedTextField(value = query, onValueChange = { query = it.take(200) },
                        modifier = Modifier.fillMaxWidth(), singleLine = true,
                        label = { Text("Search activity") }, leadingIcon = { Icon(Icons.Rounded.Search, null) },
                        trailingIcon = { if (query.isNotEmpty()) IconButton(onClick = { query = "" }) { Icon(Icons.Rounded.Close, "Clear search") } },
                        shape = RoundedCornerShape(18.dp))
                    FlowRow(Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ActivitySource.entries.forEach { option ->
                            FilterChip(selected = source == option, onClick = { source = option }, label = { Text(option.title) })
                        }
                        Box {
                            FilterChip(selected = outcome != ActivityOutcome.ALL, onClick = { outcomeMenu = true },
                                label = { Text(outcome.title) }, trailingIcon = { Icon(Icons.Rounded.ExpandMore, null) })
                            DropdownMenu(expanded = outcomeMenu, onDismissRequest = { outcomeMenu = false }) {
                                ActivityOutcome.entries.forEach { option ->
                                    DropdownMenuItem(text = { Text(option.title) }, onClick = { outcome = option; outcomeMenu = false })
                                }
                            }
                        }
                    }
                    if (loading) {
                        LinearProgressIndicator(Modifier.fillMaxWidth())
                        Text("Refreshing PC history…", style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(vertical = 6.dp))
                    }
                    phoneError?.let { ActivityNotice(it, Icons.Rounded.WarningAmber, true) }
                    error?.let { ActivityNotice(it, Icons.Rounded.CloudOff, false) }
                    Text("${visible.size} ${if (visible.size == 1) "entry" else "entries"}", style = MaterialTheme.typography.labelMedium,
                        color = colors.onSurfaceVariant, modifier = Modifier.padding(vertical = 8.dp))
                    } }
                        if (visible.isEmpty()) item {
                            Surface(shape = RoundedCornerShape(24.dp), color = colors.surfaceContainerLow) {
                                Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                    JarvisIconBadge(Icons.Rounded.History)
                                    Text(if (events.isEmpty()) "Your activity starts here" else "No matching activity",
                                        style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 12.dp))
                                    Text(if (events.isEmpty()) "Use a phone tool or connect your PC. Jarvis records metadata, not your prompts or message contents."
                                        else "Try a different search or reset the filters.", style = MaterialTheme.typography.bodyMedium,
                                        color = colors.onSurfaceVariant, modifier = Modifier.padding(top = 6.dp))
                                    if (events.isNotEmpty()) TextButton(onClick = { query = ""; source = ActivitySource.ALL; outcome = ActivityOutcome.ALL }) { Text("Reset filters") }
                                }
                            }
                        }
                        items(visible, key = { "${it.source}:${it.id}" }) { event -> ActivityCard(event) }
                }
            }
        }
        if (confirmClear) AlertDialog(onDismissRequest = { confirmClear = false },
            title = { Text("Clear phone activity?") },
            text = { Text("This removes only the activity metadata on this phone. It doesn't delete tasks, saved memories, chats, or PC logs. New actions will still be recorded.") },
            confirmButton = { TextButton(onClick = { confirmClear = false; onClearPhone() }) { Text("Clear phone log", color = colors.error) } },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("Cancel") } })
    }
}

@Composable
private fun ActivityNotice(text: String, icon: ImageVector, isError: Boolean) {
    val colors = MaterialTheme.colorScheme
    Surface(Modifier.fillMaxWidth().padding(bottom = 8.dp), shape = RoundedCornerShape(16.dp),
        color = if (isError) colors.errorContainer else colors.surfaceContainerHigh) {
        Row(Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Icon(icon, null, modifier = Modifier.size(20.dp))
            Text(text, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
        }
    }
}

@Composable
private fun ActivityCard(event: ActionAuditEvent) {
    val colors = MaterialTheme.colorScheme
    val attention = event.outcome in setOf("failed", "blocked", "rejected", "paused", "outcome_unknown")
    val pending = event.outcome in setOf("prepared", "approved", "awaiting_approval", "started")
    val icon = when {
        event.outcome == "failed" || event.outcome == "outcome_unknown" -> Icons.Rounded.ErrorOutline
        attention -> Icons.Rounded.Shield
        pending -> Icons.Rounded.PendingActions
        event.outcome in setOf("discarded", "cancelled") -> Icons.Rounded.Cancel
        event.outcome == "no_data" -> Icons.Rounded.SearchOff
        event.outcome in setOf("completed", "sent") -> Icons.Rounded.CheckCircleOutline
        else -> Icons.Rounded.Info
    }
    Surface(Modifier.fillMaxWidth(), color = colors.surfaceContainerLow, shape = RoundedCornerShape(20.dp)) {
        Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            JarvisIconBadge(icon, containerColor = if (attention) colors.errorContainer else colors.primaryContainer,
                contentColor = if (attention) colors.onErrorContainer else colors.onPrimaryContainer)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(ActionTimeline.label(event.actionType), style = MaterialTheme.typography.titleSmall)
                Text(ActionTimeline.outcomeLabel(event.outcome), style = MaterialTheme.typography.bodyMedium,
                    color = if (attention) colors.error else colors.onSurface)
                if (event.source == "pc") Text(event.summary, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
                val time = ActionTimeline.epochMillis(event.createdAt)?.let {
                    DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(it))
                } ?: "Time unavailable"
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(if (event.source == "phone") Icons.Rounded.Smartphone else Icons.Rounded.Computer, null, Modifier.size(14.dp))
                    Text("${if (event.source == "phone") "This phone" else "Paired PC"} · $time",
                        style = MaterialTheme.typography.labelSmall, color = colors.onSurfaceVariant)
                }
            }
        }
    }
}
