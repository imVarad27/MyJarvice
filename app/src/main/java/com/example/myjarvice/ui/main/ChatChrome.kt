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
    onOpenInbox: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    Row(Modifier.fillMaxWidth().heightIn(min = 64.dp).padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        ChatIconButton("Open conversation history", onOpenDrawer) { IconMenu(tint = colors.onSurfaceVariant) }
        TextButton(onClick = onStatusClick, modifier = Modifier.weight(1f)) {
            Column(Modifier.fillMaxWidth()) {
                Text("Jarvis", style = MaterialTheme.typography.titleLarge, color = colors.onSurface)
                Text(when (connectionStatus) {
                    ConnectionStatus.CONNECTED -> "PC connected"
                    ConnectionStatus.CONNECTING -> "Connecting to PC…"
                    else -> "PC offline · Connection settings"
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
    onVoiceMode: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    Surface(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        shape = RoundedCornerShape(24.dp), color = colors.surface,
        border = androidx.compose.foundation.BorderStroke(1.dp, colors.outlineVariant)) {
        Column(Modifier.padding(4.dp)) {
            OutlinedTextField(value = textInput, onValueChange = onTextChange,
                placeholder = { Text(if (canSendAttachment) "Ask about your attachment…" else "Message Jarvis…") },
                modifier = Modifier.fillMaxWidth().semantics { contentDescription = "Message input" },
                maxLines = 4,
                supportingText = { if (textInput.length >= 3600) Text("${textInput.length}/4,000 characters") },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = androidx.compose.ui.graphics.Color.Transparent,
                    unfocusedBorderColor = androidx.compose.ui.graphics.Color.Transparent))
            Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                ChatIconButton("Add attachment or tool", onToggleToolsMenu, !isThinking) { IconPlus(tint = colors.onSurfaceVariant) }
                ChatIconButton(if (isListening) "Stop dictation" else "Dictate message", onQuickVoice, !isThinking) {
                    IconMicrophone(tint = if (isListening) colors.primary else colors.onSurfaceVariant)
                }
                Spacer(Modifier.weight(1f))
                if (textInput.isNotBlank() || canSendAttachment) {
                    FilledIconButton(onClick = onSend, enabled = !isThinking,
                        modifier = Modifier.size(48.dp).semantics { contentDescription = "Send message" }) {
                        IconSend(tint = if (isThinking) colors.onSurfaceVariant else colors.onPrimary)
                    }
                } else {
                    FilledTonalButton(onClick = onVoiceMode, enabled = !isThinking, contentPadding = PaddingValues(horizontal = 16.dp)) {
                        IconVoiceWaveform(tint = colors.onSecondaryContainer, size = 18.dp)
                        Spacer(Modifier.width(8.dp)); Text("Voice")
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
                Text("PC tools", style = MaterialTheme.typography.titleSmall)
                ToolRow("Send a file to PC", "Transfer to your configured host", onSendFileToPc)
                ToolRow("Browse PC files", "Find a file on your connected computer", onOpenPcExplorer)
                var moreTools by remember { mutableStateOf(false) }
                TextButton(onClick = { moreTools = !moreTools }) { Text(if (moreTools) "Fewer tools" else "More PC tools") }
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
private fun ToolRow(title: String, subtitle: String, onClick: () -> Unit) {
    Surface(onClick = onClick, shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(vertical = 14.dp, horizontal = 8.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
