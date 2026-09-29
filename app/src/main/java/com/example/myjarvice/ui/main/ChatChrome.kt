package com.example.myjarvice.ui.main

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.myjarvice.data.ConnectionStatus
import com.example.myjarvice.data.SmartMode
import com.example.myjarvice.ui.icons.*

/** Shared, accessible touch target for the app's code-drawn icons. */
@Composable
internal fun ChatIconButton(label: String, onClick: () -> Unit, enabled: Boolean = true, icon: @Composable () -> Unit) {
    IconButton(onClick = onClick, enabled = enabled,
        modifier = Modifier.size(48.dp).semantics { contentDescription = label }) { icon() }
}

@Composable
internal fun ChatTopBar(
    connectionStatus: ConnectionStatus,
    onOpenDrawer: () -> Unit,
    onStatusClick: () -> Unit,
    onNewChat: () -> Unit,
    onOpenInbox: () -> Unit,
    mode: SmartMode = SmartMode.AUTO
) {
    val colors = MaterialTheme.colorScheme
    Row(Modifier.fillMaxWidth().heightIn(min = 60.dp).padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        ChatIconButton("Open conversation history", onOpenDrawer) { IconMenu(tint = colors.onSurfaceVariant) }
        TextButton(onClick = onStatusClick, modifier = Modifier.weight(1f).semantics { contentDescription = "Choose response mode and connection" }) {
            Column(Modifier.fillMaxWidth()) {
                Text("Jarvis", style = MaterialTheme.typography.titleLarge, color = colors.onSurface)
                Text(when {
                    mode == SmartMode.FAST_ON_DEVICE -> "On this phone"
                    connectionStatus == ConnectionStatus.CONNECTED -> "${if (mode == SmartMode.AUTO) "Auto · " else ""}PC connected"
                    connectionStatus == ConnectionStatus.CONNECTING -> "Connecting…"
                    else -> "${if (mode == SmartMode.AUTO) "Auto" else "PC"} · PC offline"
                }, style = MaterialTheme.typography.labelSmall, color = colors.onSurfaceVariant,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        TextButton(onClick = onOpenInbox, modifier = Modifier.heightIn(min = 48.dp).semantics { contentDescription = "Open saved inbox" }) {
            Text("Saved")
        }
        ChatIconButton("New conversation", onNewChat) { IconNewChat(tint = colors.onSurfaceVariant) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ChatComposer(
    textInput: String,
    onTextChange: (String) -> Unit,
    canSendAttachment: Boolean,
    isListening: Boolean,
    isThinking: Boolean,
    showToolsMenu: Boolean,
    onToggleToolsMenu: () -> Unit,
    onToolSelected: (String) -> Unit,
    onAttachFile: () -> Unit,
    onTakePhoto: () -> Unit,
    onChoosePhoto: () -> Unit,
    onSendFileToPc: () -> Unit,
    onOpenPcExplorer: () -> Unit,
    onOpenActionHistory: () -> Unit,
    onSend: () -> Unit,
    onQuickVoice: () -> Unit,
    onVoiceMode: () -> Unit,
    pcConnected: Boolean = true,
    mode: SmartMode = SmartMode.AUTO,
    onChooseModel: () -> Unit = {},
    assistantPaused: Boolean = false
) {
    val colors = MaterialTheme.colorScheme
    Surface(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        shape = RoundedCornerShape(28.dp), color = colors.surfaceContainerLow,
        border = androidx.compose.foundation.BorderStroke(1.dp, colors.primary.copy(alpha = 0.3f))) {
        Column(Modifier.padding(8.dp)) {
            OutlinedTextField(value = textInput, onValueChange = onTextChange,
                placeholder = { Text(if (canSendAttachment) "Ask about your attachment…" else "Ask Jarvis…") },
                modifier = Modifier.fillMaxWidth().semantics { contentDescription = "Message input" },
                maxLines = 4,
                textStyle = MaterialTheme.typography.bodyLarge,
                supportingText = if (textInput.length >= 3600) ({ Text("${textInput.length}/4,000 characters") }) else null,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = androidx.compose.ui.graphics.Color.Transparent,
                    unfocusedBorderColor = androidx.compose.ui.graphics.Color.Transparent))
            Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                ChatIconButton("Add attachment or tool", onToggleToolsMenu, !isThinking) { IconPlus(tint = colors.onSurfaceVariant) }
                ComposerModelSelector(mode, onChooseModel, enabled = !isThinking, modifier = Modifier.weight(1f))
                ChatIconButton(if (isListening) "Stop dictation" else "Dictate message", onQuickVoice, !isThinking && !assistantPaused) {
                    IconMicrophone(tint = if (isListening) colors.primary else colors.onSurfaceVariant)
                }
                if (textInput.isNotBlank() || canSendAttachment) {
                    FilledIconButton(onClick = onSend, enabled = !isThinking && !assistantPaused,
                        modifier = Modifier.size(48.dp).semantics { contentDescription = "Send message" }) {
                        IconSend(tint = if (isThinking) colors.onSurfaceVariant else colors.onPrimary)
                    }
                } else {
                    FilledIconButton(onClick = onVoiceMode, enabled = !isThinking && !assistantPaused,
                        modifier = Modifier.size(48.dp).semantics { contentDescription = "Start voice conversation" }) {
                        IconVoiceWaveform(tint = colors.onPrimary, size = 22.dp)
                    }
                }
            }
        }
    }
    if (showToolsMenu) {
        ModalBottomSheet(onDismissRequest = onToggleToolsMenu,
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
            val shortcuts = listOf(
                Shortcut("Take a photo", "Capture a receipt, label, or page", "Attachments", onTakePhoto),
                Shortcut("Choose a photo", "Attach an image from your phone", "Attachments", onChoosePhoto),
                Shortcut("Attach text document", "Text or Markdown, up to 8,000 characters", "Attachments", onAttachFile),
                Shortcut("Phone status", "Battery, charging and connection", "On your phone", { onToolSelected("phone status") }),
                Shortcut("Flashlight", "Prepare a flashlight command", "On your phone", { onToolSelected("turn the flashlight on") }),
                Shortcut("Set a timer", "Choose a duration before you send", "On your phone", { onToolSelected("start a timer for 10 minutes") }),
                Shortcut("Saved memories", "Facts stored on this phone", "On your phone", { onToolSelected("show memories") }),
                Shortcut("Plan my day", "Review your PC tasks and reminders", "PC assistant", { onToolSelected("plan my day") }, true),
                Shortcut("My tasks", "Review saved PC tasks", "PC assistant", { onToolSelected("show my tasks") }, true),
                Shortcut("Daily briefing", "Prepare a briefing request", "PC assistant", { onToolSelected("Give me my executive morning briefing.") }, true),
                Shortcut("Active reminders", "Review PC reminders", "PC assistant", { onToolSelected("What are my active reminders?") }, true),
                Shortcut("Web search", "Add a topic before sending", "PC assistant", { onToolSelected("Search the web with sources about: ") }, true),
                Shortcut("Weather", "Add a city before sending", "PC assistant", { onToolSelected("What is the live weather forecast for ") }, true),
                Shortcut("Activity", "Review recent PC actions", "PC tools", onOpenActionHistory, true),
                Shortcut("Send a file to PC", "Choose a file to transfer", "PC tools", onSendFileToPc, true),
                Shortcut("Browse PC files", "Find a file on your computer", "PC tools", onOpenPcExplorer, true),
                Shortcut("PC screenshot", "Prepare a screen capture request", "PC tools", { onToolSelected("Capture host PC screenshot.") }, true),
                Shortcut("PC hardware status", "Check your computer's resources", "PC tools", { onToolSelected("What are my PC hardware stats?") }, true),
                Shortcut("Lock PC", "Prepare a workstation lock request", "PC tools", { onToolSelected("Lock my host PC workstation.") }, true),
                Shortcut("Search PC documents", "Search indexed files", "PC tools", { onToolSelected("Search indexed files on my PC drives.") }, true)
            )
            ShortcutBrowser(shortcuts, pcConnected, assistantPaused)
        }
    }
}

private data class Shortcut(val title: String, val subtitle: String, val group: String,
    val run: () -> Unit, val needsPc: Boolean = false)

@Composable
private fun ShortcutBrowser(shortcuts: List<Shortcut>, pcConnected: Boolean, paused: Boolean) {
    var query by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf("") }
    var phoneOnly by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(false) }
    val needle = query.trim()
    val matches = shortcuts.filter {
        (!phoneOnly || !it.needsPc) && "${it.title} ${it.subtitle} ${it.group}".contains(needle, ignoreCase = true)
    }
    Column(Modifier.fillMaxWidth().imePadding().padding(horizontal = 24.dp).padding(bottom = 16.dp)) {
        Text("Your shortcuts", style = MaterialTheme.typography.headlineSmall)
        Text(if (paused) "Jarvis is paused. Resume in Settings to use tools." else "Commands open in chat for you to review.",
            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp, bottom = 12.dp))
        OutlinedTextField(query, { query = it }, singleLine = true, modifier = Modifier.fillMaxWidth(),
            label = { Text("Find a shortcut") }, shape = RoundedCornerShape(20.dp),
            trailingIcon = { if (query.isNotEmpty()) TextButton(onClick = { query = "" }) { Text("Clear") } })
        FilterChip(selected = phoneOnly, onClick = { phoneOnly = !phoneOnly }, label = { Text("Works without PC") })
        androidx.compose.foundation.lazy.LazyColumn(Modifier.weight(1f, fill = false)) {
            if (matches.isEmpty()) item {
                Text("No shortcuts found. Try camera, timer, or memory.", modifier = Modifier.padding(vertical = 24.dp))
            }
            matches.groupBy { it.group }.forEach { (group, entries) ->
                item(key = group) { Text(group, style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.padding(top = 12.dp, bottom = 8.dp)) }
                entries.forEach { shortcut ->
                    item(key = shortcut.title) {
                        ToolRow(shortcut.title, shortcut.subtitle + if (shortcut.needsPc && !pcConnected) " · PC unavailable" else "",
                            shortcut.run, enabled = !paused && (!shortcut.needsPc || pcConnected))
                    }
                }
            }
        }
    }
}

@Composable
internal fun ComposerModelSelector(mode: SmartMode, onClick: () -> Unit, enabled: Boolean = true,
    modifier: Modifier = Modifier) {
    TextButton(onClick = onClick, enabled = enabled,
        modifier = modifier.heightIn(min = 48.dp).semantics { contentDescription = "Choose AI model: ${modeLabel(mode)}" },
        contentPadding = PaddingValues(horizontal = 10.dp)) {
        Text("${modeLabel(mode)} ⌄", style = MaterialTheme.typography.labelLarge,
            maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

internal fun modeLabel(mode: SmartMode) = when (mode) {
    SmartMode.AUTO -> "Auto"
    SmartMode.FAST_ON_DEVICE -> "Phone"
    SmartMode.STRONG_HOST -> "PC"
}

@Composable
private fun ToolRow(title: String, subtitle: String, onClick: () -> Unit, enabled: Boolean = true) {
    val colors = MaterialTheme.colorScheme
    Surface(onClick = onClick, enabled = enabled, shape = RoundedCornerShape(16.dp),
        color = colors.surfaceContainerLow,
        contentColor = if (enabled) colors.onSurface else colors.onSurface.copy(alpha = 0.38f),
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Column(Modifier.padding(vertical = 14.dp, horizontal = 16.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(subtitle, style = MaterialTheme.typography.bodyMedium,
                color = if (enabled) colors.onSurfaceVariant else colors.onSurface.copy(alpha = 0.38f),
                modifier = Modifier.padding(top = 4.dp))
        }
    }
}
