package com.example.myjarvice.ui.main

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.myjarvice.data.ChatSession

/** Scrollable navigation, not a second dashboard. Destructive actions still request approval in MainScreen. */
@Composable
internal fun HistoryDrawerContent(savedSessions: List<ChatSession>, onNewChat: () -> Unit,
    onSelectSession: (ChatSession) -> Unit, onDeleteSession: (String) -> Unit,
    onClearAllHistory: () -> Unit, onOpenSettings: () -> Unit, onOpenToday: () -> Unit, onOpenInbox: () -> Unit,
    canChangeConversation: Boolean = true) {
    val colors = MaterialTheme.colorScheme
    var query by rememberSaveable { mutableStateOf("") }
    var options by remember { mutableStateOf(false) }
    val needle = query.trim()
    val matches = remember(savedSessions, needle) { savedSessions.filter { session ->
        needle.isEmpty() || session.title.contains(needle, true) || session.messages.any { it.text.contains(needle, true) }
    } }
    Column(Modifier.fillMaxSize().imePadding().padding(horizontal = 12.dp)) {
        LazyColumn(Modifier.weight(1f).fillMaxWidth(), contentPadding = PaddingValues(vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)) {
            item(key = "brand") { Text("Jarvis", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(12.dp)) }
            item(key = "new") {
                FilledTonalButton(onClick = onNewChat, enabled = canChangeConversation, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                    shape = RoundedCornerShape(16.dp), colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = colors.surfaceContainerHigh, contentColor = colors.onSurface)) {
                    Icon(Icons.Rounded.Edit, null, modifier = Modifier.size(20.dp))
                    Text("New chat", modifier = Modifier.weight(1f).padding(start = 12.dp))
                }
            }
            item(key = "destinations") { DrawerAssistantLinks(onOpenToday, onOpenInbox) }
            item(key = "search") {
                OutlinedTextField(query, { query = it }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                    label = { Text("Search conversations") }, shape = RoundedCornerShape(16.dp),
                    leadingIcon = { Icon(Icons.Rounded.Search, null) }, trailingIcon = {
                        if (query.isNotEmpty()) IconButton(onClick = { query = "" }) { Icon(Icons.Rounded.Close, "Clear conversation search") }
                    })
            }
            item(key = "heading") {
                Text(if (needle.isEmpty()) "Conversations" else "${matches.size} results", style = MaterialTheme.typography.labelMedium,
                    color = colors.onSurfaceVariant, modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp))
            }
            if (matches.isEmpty()) item(key = "empty") {
                Text(if (needle.isEmpty()) "Your conversations will appear here" else "No conversations match your search",
                    style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant, modifier = Modifier.padding(12.dp))
            }
            items(matches, key = { it.id }) { session ->
                Surface(onClick = { onSelectSession(session) }, enabled = canChangeConversation, shape = RoundedCornerShape(14.dp), color = colors.surface,
                    modifier = Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(start = 12.dp, top = 4.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(session.title, style = MaterialTheme.typography.bodyMedium, maxLines = 2,
                            overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                        IconButton(onClick = { onDeleteSession(session.id) }, enabled = canChangeConversation, modifier = Modifier.size(48.dp)) {
                            Icon(Icons.Rounded.DeleteOutline, "Delete conversation ${session.title}", tint = colors.onSurfaceVariant,
                                modifier = Modifier.size(20.dp))
                        }
                    }
                }
            }
        }
        HorizontalDivider(color = colors.outlineVariant)
        if (!canChangeConversation) Text("Reply in progress", style = MaterialTheme.typography.labelSmall,
            color = colors.onSurfaceVariant, modifier = Modifier.padding(start = 12.dp, top = 8.dp))
        Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onOpenSettings, modifier = Modifier.weight(1f).heightIn(min = 48.dp)) {
                Icon(Icons.Rounded.Settings, null, tint = colors.onSurfaceVariant)
                Text("Settings", modifier = Modifier.weight(1f).padding(start = 12.dp), color = colors.onSurface)
            }
            if (savedSessions.isNotEmpty()) Box {
                IconButton(onClick = { options = true }, modifier = Modifier.size(48.dp)) {
                    Icon(Icons.Rounded.MoreHoriz, "Conversation options")
                }
                DropdownMenu(expanded = options, onDismissRequest = { options = false }) {
                    DropdownMenuItem(text = { Text("Clear conversations", color = colors.error) },
                        enabled = canChangeConversation,
                        leadingIcon = { Icon(Icons.Rounded.DeleteOutline, null, tint = colors.error) },
                        onClick = { options = false; onClearAllHistory() })
                }
            }
        }
    }
}
