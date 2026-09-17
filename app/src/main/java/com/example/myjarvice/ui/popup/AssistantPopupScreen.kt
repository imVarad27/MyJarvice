package com.example.myjarvice.ui.popup

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.pm.PackageManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.myjarvice.theme.AssistantStyle
import com.example.myjarvice.ui.icons.*
import com.example.myjarvice.ui.main.ChatFeed
import com.example.myjarvice.ui.main.ComposerModelSelector
import com.example.myjarvice.ui.main.EmailApprovalDialog
import com.example.myjarvice.ui.main.MainScreenViewModel
import com.example.myjarvice.ui.main.ResponseModeSheet
import com.example.myjarvice.ui.main.ServerConfigDialog
import com.example.myjarvice.data.ConnectionStatus

/** A compact, original Jarvis card inspired by Material/Pixel assistant surfaces. */
@Composable
fun AssistantPopupScreen(model: MainScreenViewModel, verifiedWake: Boolean,
    style: AssistantStyle, onDismiss: () -> Unit, onExpand: () -> Unit) {
    val context = LocalContext.current
    val colors = MaterialTheme.colorScheme
    val focus = LocalFocusManager.current
    val mode by model.smartMode.collectAsStateWithLifecycle()
    val connection by model.connectionStatus.collectAsStateWithLifecycle()
    val serverIp by model.serverIp.collectAsStateWithLifecycle()
    val serverToken by model.serverToken.collectAsStateWithLifecycle()
    val messages by model.chatHistory.collectAsStateWithLifecycle()
    val listening by model.isListening.collectAsStateWithLifecycle()
    val speaking by model.isSpeaking.collectAsStateWithLifecycle()
    val thinking by model.isThinking.collectAsStateWithLifecycle()
    val route by model.responseRoute.collectAsStateWithLifecycle()
    val transcript by model.liveTranscript.collectAsStateWithLifecycle()
    val status by model.recognitionStatus.collectAsStateWithLifecycle()
    val owner by model.voiceOwnerVerified.collectAsStateWithLifecycle()
    val muted by model.micMuted.collectAsStateWithLifecycle()
    val action by model.pendingAction.collectAsStateWithLifecycle()
    val email by model.pendingEmail.collectAsStateWithLifecycle()
    var text by rememberSaveable { mutableStateOf("") }
    var chooseModel by remember { mutableStateOf(false) }
    var chooseConnection by remember { mutableStateOf(false) }

    val microphonePermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) model.enterVoiceMode()
    }
    LaunchedEffect(Unit) {
        if (verifiedWake) model.enterVoiceMode(verifiedByWake = true, matchedOwner = true)
    }
    BackHandler { onDismiss() }

    Box(Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxSize().clickable(onClickLabel = "Dismiss Jarvis popup", onClick = onDismiss))
        Surface(onClick = {},
            modifier = Modifier.align(Alignment.BottomCenter).imePadding().navigationBarsPadding()
                .padding(horizontal = 12.dp, vertical = 10.dp).widthIn(max = 600.dp).fillMaxWidth(),
            shape = RoundedCornerShape(32.dp), color = colors.surfaceContainerLowest,
            border = BorderStroke(2.dp, Brush.linearGradient(
                if (style == AssistantStyle.JARVIS) listOf(colors.primary, colors.tertiary, colors.primary)
                else listOf(colors.primary.copy(alpha = .7f), colors.secondaryContainer, colors.primary.copy(alpha = .7f))
            )), shadowElevation = 16.dp) {
            Column(Modifier.heightIn(max = 520.dp).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 10.dp)) {
                Box(Modifier.align(Alignment.CenterHorizontally).padding(bottom = 8.dp).size(36.dp, 4.dp)) {
                    Surface(color = colors.outlineVariant, shape = RoundedCornerShape(4.dp), modifier = Modifier.fillMaxSize()) {}
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    JarvisMark(colors.primary, colors.tertiary, Modifier.size(32.dp))
                    Column(Modifier.weight(1f).padding(start = 10.dp)) {
                        Text("Jarvis", style = MaterialTheme.typography.titleMedium)
                        Text(if (owner) "Wake voice matched" else "Your personal assistant",
                            style = MaterialTheme.typography.labelSmall, color = colors.onSurfaceVariant)
                    }
                    TextButton(onClick = onExpand, enabled = !thinking) { Text("Expand") }
                    IconButton(onClick = onDismiss, modifier = Modifier.semantics { contentDescription = "Close Jarvis" }) {
                        Text("×", style = MaterialTheme.typography.headlineSmall)
                    }
                }
                if (messages.isNotEmpty()) {
                    ChatFeed(messages, thinking, onCopy = {
                        (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager)
                            .setPrimaryClip(ClipData.newPlainText("Jarvis reply", it))
                    }, onSpeak = model::speak,
                        modifier = Modifier.fillMaxWidth().heightIn(max = 230.dp), processingLabel = route)
                }
                if (listening || speaking || thinking || (!muted && status.isNotBlank())) {
                    Text(when {
                        thinking -> route
                        speaking -> "Speaking…"
                        listening && transcript.isNotBlank() -> transcript
                        else -> status
                    }, style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp))
                }
                OutlinedTextField(value = text, onValueChange = { text = it.take(4000) },
                    placeholder = { Text("Ask Jarvis…") }, maxLines = 4,
                    modifier = Modifier.fillMaxWidth().onFocusChanged { if (it.isFocused) model.pauseVoiceForTyping() }
                        .semantics { contentDescription = "Popup message input" },
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Color.Transparent, unfocusedBorderColor = Color.Transparent))
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    IconButton(onClick = onExpand, enabled = !thinking,
                        modifier = Modifier.semantics { contentDescription = "Open attachments and tools in full chat" }) {
                        IconPlus(tint = colors.onSurfaceVariant)
                    }
                    ComposerModelSelector(mode, { chooseModel = true; model.pauseVoiceForTyping() },
                        enabled = !thinking, modifier = Modifier.weight(1f))
                    FilledIconButton(onClick = {
                        focus.clearFocus()
                        if (text.isNotBlank()) {
                            model.pauseVoiceForTyping()
                            model.sendQuery(text)
                            text = ""
                        } else if (listening) model.pauseVoiceForTyping()
                        else if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) model.enterVoiceMode()
                        else microphonePermission.launch(Manifest.permission.RECORD_AUDIO)
                    }, enabled = !thinking, modifier = Modifier.size(48.dp).semantics {
                        contentDescription = if (text.isNotBlank()) "Send popup message" else if (listening) "Stop popup microphone" else "Start popup microphone"
                    }) {
                        if (text.isNotBlank()) IconSend(tint = colors.onPrimary)
                        else IconMicrophone(tint = colors.onPrimary)
                    }
                }
                Text(when (mode) {
                    com.example.myjarvice.data.SmartMode.FAST_ON_DEVICE -> "Text generation stays on your phone"
                    com.example.myjarvice.data.SmartMode.STRONG_HOST -> if (connection == ConnectionStatus.CONNECTED) "Uses your paired PC model" else "PC offline · connect through the model menu"
                    else -> "PC when connected · phone fallback when available"
                }, style = MaterialTheme.typography.labelSmall, color = colors.onSurfaceVariant,
                    modifier = Modifier.padding(start = 8.dp, bottom = 4.dp))
            }
        }
    }
    if (chooseModel) ResponseModeSheet(mode, connection, model.hasOnDeviceModel(),
        onSelect = { model.selectSmartMode(it); chooseModel = false },
        onConnect = { chooseModel = false; chooseConnection = true }, onDismiss = { chooseModel = false })
    if (chooseConnection) ServerConfigDialog(serverIp, serverToken,
        onConnect = { ip, token -> model.updateServerConnection(ip, token); chooseConnection = false },
        onDismiss = { chooseConnection = false })
    action?.let { pending ->
        AlertDialog(onDismissRequest = { model.resolvePendingAction(false) },
            title = { Text("Confirm action") }, text = { Text("${pending.type.lowercase()}: ${pending.query}") },
            confirmButton = { TextButton(onClick = { model.resolvePendingAction(true) }) { Text("Allow") } },
            dismissButton = { TextButton(onClick = { model.resolvePendingAction(false) }) { Text("Cancel") } })
    }
    email?.let { draft -> EmailApprovalDialog(draft,
        onApprove = { model.resolvePendingEmail(draft.id, true) },
        onDiscard = { model.resolvePendingEmail(draft.id, false) }) }
}

@Composable
private fun JarvisMark(primary: Color, accent: Color, modifier: Modifier) {
    Canvas(modifier) {
        drawCircle(primary.copy(alpha = .12f))
        drawArc(primary, -65f, 280f, false, topLeft = Offset(size.width * .12f, size.height * .12f),
            size = androidx.compose.ui.geometry.Size(size.width * .76f, size.height * .76f),
            style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round))
        drawCircle(accent, radius = size.minDimension * .11f)
    }
}
