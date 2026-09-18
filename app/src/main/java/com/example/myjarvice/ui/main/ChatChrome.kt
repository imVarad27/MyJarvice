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
                Text("Jarvis ⌄", style = MaterialTheme.typography.titleLarge, color = colors.onSurface)
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
    onSend: () -> Unit,
    onQuickVoice: () -> Unit,
    onVoiceMode: () -> Unit,
    pcConnected: Boolean = true,
    mode: SmartMode = SmartMode.AUTO,
    onChooseModel: () -> Unit = {}
) {
    val colors = MaterialTheme.colorScheme
    Surface(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        shape = RoundedCornerShape(28.dp), color = colors.surfaceContainerLow,
        border = androidx.compose.foundation.BorderStroke(1.dp, colors.outlineVariant)) {
        Column(Modifier.padding(4.dp)) {
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
                ChatIconButton(if (isListening) "Stop dictation" else "Dictate message", onQuickVoice, !isThinking) {
                    IconMicrophone(tint = if (isListening) colors.primary else colors.onSurfaceVariant)
                }
                if (textInput.isNotBlank() || canSendAttachment) {
                    FilledIconButton(onClick = onSend, enabled = !isThinking,
                        modifier = Modifier.size(48.dp).semantics { contentDescription = "Send message" }) {
                        IconSend(tint = if (isThinking) colors.onSurfaceVariant else colors.onPrimary)
                    }
                } else {
                    FilledIconButton(onClick = onVoiceMode, enabled = !isThinking,
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
            Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp).padding(bottom = 24.dp)) {
                Text("Add to your conversation", style = MaterialTheme.typography.titleLarge)
                Text("Choose something to share with Jarvis.", style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp, bottom = 16.dp))
                ToolRow("Take a photo", "Capture a receipt, label, or page", onTakePhoto)
                ToolRow("Choose a photo", "Attach an image from your phone", onChoosePhoto)
                ToolRow("Attach text document", "Text or Markdown, up to 8,000 characters", onAttachFile)
                HorizontalDivider(Modifier.padding(vertical = 12.dp))
                Text("Phone actions", style = MaterialTheme.typography.titleSmall)
                ToolRow("Phone status", "Battery, charging and connection", { onToolSelected("phone status") })
                ToolRow("Turn flashlight on", "Safe immediate action", { onToolSelected("turn the flashlight on") })
                ToolRow("Set a timer", "Android asks you to confirm the duration", { onToolSelected("start a timer for 10 minutes") })
                HorizontalDivider(Modifier.padding(vertical = 12.dp))
                Text("Personal assistant", style = MaterialTheme.typography.titleSmall)
                ToolRow("Plan my day", "Open tasks and reminders · PC required", { onToolSelected("plan my day") }, pcConnected)
                ToolRow("My tasks", "Review saved tasks · PC required", { onToolSelected("show my tasks") }, pcConnected)
                ToolRow("Saved memories", "Review facts stored on this phone", { onToolSelected("show memories") })
                HorizontalDivider(Modifier.padding(vertical = 12.dp))
                Text(if (pcConnected) "Connected PC" else "PC tools · connect your PC to use", style = MaterialTheme.typography.titleSmall)
                ToolRow("Send a file to PC", "Transfer to your configured host", onSendFileToPc, pcConnected)
                ToolRow("Browse PC files", "Find a file on your connected computer", onOpenPcExplorer, pcConnected)
                var moreTools by remember { mutableStateOf(false) }
                TextButton(onClick = { moreTools = !moreTools }, enabled = pcConnected) { Text(if (moreTools) "Fewer tools" else "More PC tools") }
                if (moreTools) {
                    listOf(
                        "Daily briefing" to "Give me my executive morning briefing.",
                        "Active reminders" to "What are my active reminders?",
                        "Web search" to "Search the web for the latest artificial intelligence news.",
                        "Weather" to "What is the live weather forecast for Pune today?",
                        "PC screenshot" to "Capture host PC screenshot.",
                        "PC hardware status" to "What are my PC hardware stats?",
                        "Lock PC" to "Lock my host PC workstation.",
                        "Search PC documents" to "Search indexed files on my PC drives."
                    ).forEach { (label, prompt) -> TextButton(onClick = { onToolSelected(prompt) }) { Text(label) } }
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
    Surface(onClick = onClick, enabled = enabled, shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(vertical = 14.dp, horizontal = 8.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
