package com.example.myjarvice.ui.main

import android.app.Application
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.myjarvice.data.ConnectionStatus
import com.example.myjarvice.data.JarviceMessage
import com.example.myjarvice.data.SettingsStore
import com.example.myjarvice.theme.AiBubbleBg
import com.example.myjarvice.theme.ArcGold
import com.example.myjarvice.theme.JarvisBgBottom
import com.example.myjarvice.theme.JarvisBgTop
import com.example.myjarvice.theme.JarvisBlue
import com.example.myjarvice.theme.JarvisCyan
import com.example.myjarvice.theme.JarvisDarkBackground
import com.example.myjarvice.theme.JarvisSurfaceBorder
import com.example.myjarvice.theme.JarvisSurfaceDark
import com.example.myjarvice.theme.OfflineGray
import com.example.myjarvice.theme.OnlineGreen
import com.example.myjarvice.theme.TextPrimary
import com.example.myjarvice.theme.TextSecondary
import com.example.myjarvice.theme.UserBubbleBg
import com.example.myjarvice.ui.JarvisArcReactor
import com.example.myjarvice.ui.voice.VoiceInfoDialog
import com.example.myjarvice.ui.voice.VoiceModeScreen
import com.example.myjarvice.ui.voice.VoicePickerDialog
import com.example.myjarvice.wake.WakeWordService

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun MainScreen(
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val viewModel: MainScreenViewModel = viewModel {
        MainScreenViewModel(context.applicationContext as Application)
    }

    val connectionStatus by viewModel.connectionStatus.collectAsStateWithLifecycle()
    val chatHistory by viewModel.chatHistory.collectAsStateWithLifecycle()
    val isSpeaking by viewModel.isSpeaking.collectAsStateWithLifecycle()
    val isListening by viewModel.isListening.collectAsStateWithLifecycle()
    val serverIp by viewModel.serverIp.collectAsStateWithLifecycle()
    val serverToken by viewModel.serverToken.collectAsStateWithLifecycle()
    val pendingAction by viewModel.pendingAction.collectAsStateWithLifecycle()
    val voiceModeActive by viewModel.voiceModeActive.collectAsStateWithLifecycle()
    val micMuted by viewModel.micMuted.collectAsStateWithLifecycle()
    val micLevel by viewModel.micLevel.collectAsStateWithLifecycle()
    val voices by viewModel.voices.collectAsStateWithLifecycle()
    val selectedVoiceId by viewModel.selectedVoiceId.collectAsStateWithLifecycle()
    val pendingEmail by viewModel.pendingEmail.collectAsStateWithLifecycle()
    val isThinking by viewModel.isThinking.collectAsStateWithLifecycle()

    // Exactly one recogniser may own the microphone at a time: the always-on wake-word
    // service (Vosk) or the in-app recogniser. Whichever loses gets fed silence by
    // Android. So they take turns — the wake word yields while the chat is on screen,
    // and takes the mic back the moment we leave.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val settings = SettingsStore(context.applicationContext)

        // ON_START has usually already fired by the time this observer is registered,
        // so claim the microphone immediately rather than waiting for an event that
        // will not arrive until the next foreground trip.
        WakeWordService.stop(context.applicationContext)

        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> WakeWordService.stop(context.applicationContext)
                Lifecycle.Event.ON_STOP -> {
                    viewModel.exitVoiceMode()
                    if (settings.wakeWordEnabled) {
                        WakeWordService.start(context.applicationContext)
                    }
                }
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    var textInput by remember { mutableStateOf("") }
    var showIpDialog by remember { mutableStateOf(false) }
    var showVoiceInfo by remember { mutableStateOf(false) }
    var showVoicePicker by remember { mutableStateOf(false) }

    if (showIpDialog) {
        ServerConfigDialog(
            currentIp = serverIp,
            currentToken = serverToken,
            onConnect = { ip, token ->
                viewModel.updateServerConnection(ip, token)
                android.widget.Toast.makeText(context, "Connecting to $ip...", android.widget.Toast.LENGTH_SHORT).show()
                showIpDialog = false
            },
            onDismiss = { showIpDialog = false }
        )
    }

    pendingEmail?.let { draft ->
        EmailApprovalDialog(
            draft = draft,
            onApprove = { viewModel.resolvePendingEmail(draft.id, approved = true) },
            onDiscard = { viewModel.resolvePendingEmail(draft.id, approved = false) }
        )
    }

    pendingAction?.let { action ->
        AlertDialog(
            onDismissRequest = { viewModel.resolvePendingAction(false) },
            title = { Text("Confirm phone action") },
            text = { Text("Allow JARVICE to ${action.type.lowercase().replace('_', ' ')}: ${action.query}?") },
            confirmButton = { TextButton(onClick = { viewModel.resolvePendingAction(true) }) { Text("Allow") } },
            dismissButton = { TextButton(onClick = { viewModel.resolvePendingAction(false) }) { Text("Deny") } }
        )
    }

    if (showVoiceInfo) {
        VoiceInfoDialog(
            serverIp = serverIp,
            connectionStatus = connectionStatus,
            messageCount = chatHistory.size,
            voiceLabel = voices.firstOrNull { it.id == selectedVoiceId }?.label ?: "Engine default",
            onDismiss = { showVoiceInfo = false }
        )
    }

    if (showVoicePicker) {
        VoicePickerDialog(
            voices = voices,
            selectedVoiceId = selectedVoiceId,
            onSelect = { viewModel.selectVoice(it) },
            onDismiss = { showVoicePicker = false }
        )
    }

    val screenGradient = Brush.verticalGradient(listOf(JarvisBgTop, JarvisBgBottom))

    // The keyboard steals roughly 40% of the viewport. Collapsing the reactor and
    // the quick-action chips keeps the chat feed and input bar usable instead of
    // letting the fixed-height chrome squeeze the feed's weight down to nothing.
    val imeVisible = WindowInsets.isImeVisible

    Box(modifier = modifier.fillMaxSize()) {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            containerColor = JarvisDarkBackground
        ) { innerPadding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(screenGradient)
                    .padding(innerPadding)
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // The whole HUD folds away while the keyboard is up. This device pans the
                // window by a fixed amount rather than resizing it, so anything left above
                // the feed is scrolled off-screen anyway — better to reclaim the space and
                // give the chat feed the entire visible band.
                AnimatedVisibility(visible = !imeVisible) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        HudHeader(
                            serverIp = serverIp,
                            connectionStatus = connectionStatus,
                            onStatusClick = { showIpDialog = true }
                        )

                        Spacer(Modifier.height(14.dp))

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(230.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            JarvisArcReactor(isListening = isListening, isSpeaking = isSpeaking)
                        }

                        val (statusLine, statusLineColor) = when {
                            isThinking -> ">>> THINKING <<<" to JarvisBlue
                            isListening -> ">>> LISTENING <<<" to OnlineGreen
                            isSpeaking -> ">>> SPEAKING <<<" to ArcGold
                            connectionStatus == ConnectionStatus.CONNECTED -> "NEURAL CORE READY" to JarvisCyan
                            connectionStatus == ConnectionStatus.CONNECTING -> "ESTABLISHING LINK..." to ArcGold
                            else -> "OFFLINE — TAP STATUS TO SET IP" to OfflineGray
                        }
                        Text(
                            text = statusLine,
                            color = statusLineColor,
                            fontSize = 12.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.SemiBold,
                            letterSpacing = 2.sp
                        )

                        Spacer(Modifier.height(14.dp))

                        QuickActionRow(onPrompt = { viewModel.sendQuery(it) })

                        Spacer(Modifier.height(12.dp))
                    }
                }

                // --- CHAT FEED ---
                ChatFeed(
                    chatHistory = chatHistory,
                    imeVisible = imeVisible,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                )

                Spacer(Modifier.height(10.dp))

                InputBar(
                    textInput = textInput,
                    onTextChange = { textInput = it },
                    isListening = isListening,
                    onSend = {
                        if (textInput.isNotBlank()) {
                            viewModel.sendQuery(textInput)
                            textInput = ""
                        } else {
                            viewModel.enterVoiceMode()
                        }
                    }
                )
            }
        }

        AnimatedVisibility(
            visible = voiceModeActive,
            enter = fadeIn(animationSpec = tween(220)),
            exit = fadeOut(animationSpec = tween(180))
        ) {
            VoiceModeScreen(
                isListening = isListening,
                isSpeaking = isSpeaking,
                isThinking = isThinking,
                micMuted = micMuted,
                micLevel = micLevel,
                onToggleMute = { viewModel.toggleMute() },
                onClose = { viewModel.exitVoiceMode() },
                onInfo = { showVoiceInfo = true },
                onShare = { shareTranscript(context, viewModel.buildTranscript()) },
                onChangeVoice = { showVoicePicker = true }
            )
        }
    }
}

/** Hands the conversation transcript to the system share sheet. */
private fun shareTranscript(context: android.content.Context, transcript: String) {
    if (transcript.isBlank()) {
        android.widget.Toast
            .makeText(context, "Nothing to share yet.", android.widget.Toast.LENGTH_SHORT)
            .show()
        return
    }
    val send = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(android.content.Intent.EXTRA_SUBJECT, "Jarvis conversation")
        putExtra(android.content.Intent.EXTRA_TEXT, transcript)
    }
    context.startActivity(android.content.Intent.createChooser(send, "Share transcript"))
}

@Composable
private fun HudHeader(
    serverIp: String,
    connectionStatus: ConnectionStatus,
    onStatusClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(JarvisSurfaceDark.copy(alpha = 0.85f))
            .border(1.dp, JarvisSurfaceBorder, RoundedCornerShape(14.dp))
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column {
            Text(
                "J A R V I S",
                color = JarvisCyan,
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp,
                fontFamily = FontFamily.Monospace,
                letterSpacing = 3.sp
            )
            Text(
                "HOST · $serverIp:8000",
                color = TextSecondary,
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace
            )
        }

        val (dotColor, statusText) = when (connectionStatus) {
            ConnectionStatus.CONNECTED -> OnlineGreen to "ONLINE"
            ConnectionStatus.CONNECTING -> ArcGold to "LINKING"
            ConnectionStatus.DISCONNECTED -> OfflineGray to "OFFLINE"
            ConnectionStatus.ERROR -> Color(0xFFFF5A5A) to "ERROR"
        }

        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(20.dp))
                .background(dotColor.copy(alpha = 0.12f))
                .border(1.dp, dotColor.copy(alpha = 0.5f), RoundedCornerShape(20.dp))
                .clickable { onStatusClick() }
                .padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(dotColor)
            )
            Spacer(Modifier.width(6.dp))
            Text(
                statusText,
                color = dotColor,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                letterSpacing = 1.sp
            )
        }
    }
}

@Composable
private fun QuickActionRow(onPrompt: (String) -> Unit) {
    val quickPrompts = listOf(
        "Search my docs" to "📄",
        "System status" to "⚡",
        "Turn on lab lights" to "💡",
        "My schedule" to "📅",
        "Weather report" to "🌤️"
    )
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        items(quickPrompts) { (label, icon) ->
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(18.dp))
                    .background(JarvisSurfaceDark)
                    .border(1.dp, JarvisCyan.copy(alpha = 0.3f), RoundedCornerShape(18.dp))
                    .clickable { onPrompt(label) }
                    .padding(horizontal = 12.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(icon, fontSize = 12.sp)
                Spacer(Modifier.width(5.dp))
                Text(label, color = TextPrimary, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
            }
        }
    }
}

@Composable
private fun ChatFeed(
    chatHistory: List<JarviceMessage>,
    imeVisible: Boolean,
    modifier: Modifier = Modifier
) {
    val listState = rememberLazyListState()

    // Auto-scroll to the newest message — also when the keyboard opens or closes,
    // since the feed is resized and would otherwise strand the user mid-history.
    LaunchedEffect(chatHistory.size, imeVisible) {
        if (chatHistory.isNotEmpty()) {
            listState.animateScrollToItem(chatHistory.size - 1)
        }
    }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(JarvisSurfaceDark.copy(alpha = 0.55f))
            .border(1.dp, JarvisSurfaceBorder, RoundedCornerShape(14.dp))
    ) {
        if (chatHistory.isEmpty()) {
            Column(
                modifier = Modifier.fillMaxSize().padding(24.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("◈", color = JarvisCyan.copy(alpha = 0.5f), fontSize = 34.sp)
                Spacer(Modifier.height(10.dp))
                Text(
                    "Awaiting your command, Sir.",
                    color = TextSecondary,
                    fontSize = 13.sp,
                    fontFamily = FontFamily.Monospace,
                    textAlign = TextAlign.Center
                )
                Text(
                    "Try a chip above or type below.",
                    color = TextSecondary.copy(alpha = 0.6f),
                    fontSize = 11.sp,
                    textAlign = TextAlign.Center
                )
            }
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize().padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(chatHistory) { msg -> ChatBubble(msg) }
            }
        }
    }
}

@Composable
private fun InputBar(
    textInput: String,
    onTextChange: (String) -> Unit,
    isListening: Boolean,
    onSend: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        OutlinedTextField(
            value = textInput,
            onValueChange = onTextChange,
            placeholder = { Text("Command Jarvis...", color = TextSecondary, fontSize = 13.sp) },
            modifier = Modifier.weight(1f),
            singleLine = true,
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = JarvisCyan,
                unfocusedBorderColor = JarvisSurfaceBorder,
                focusedTextColor = TextPrimary,
                unfocusedTextColor = TextPrimary,
                cursorColor = JarvisCyan,
                focusedContainerColor = JarvisSurfaceDark.copy(alpha = 0.6f),
                unfocusedContainerColor = JarvisSurfaceDark.copy(alpha = 0.6f)
            ),
            shape = RoundedCornerShape(24.dp)
        )

        Spacer(Modifier.width(8.dp))

        Button(
            onClick = onSend,
            colors = ButtonDefaults.buttonColors(
                containerColor = if (isListening) OnlineGreen else JarvisCyan
            ),
            shape = CircleShape,
            contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp),
            modifier = Modifier.size(52.dp)
        ) {
            Text(
                if (isListening) "🎙️" else if (textInput.isBlank()) "🎤" else "➤",
                fontSize = 18.sp,
                color = JarvisDarkBackground
            )
        }
    }
}

@Composable
private fun ServerConfigDialog(
    currentIp: String,
    currentToken: String,
    onConnect: (String, String) -> Unit,
    onDismiss: () -> Unit
) {
    var tempIp by remember { mutableStateOf(currentIp) }
    var tempToken by remember { mutableStateOf(currentToken) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Server Config", color = JarvisCyan, fontFamily = FontFamily.Monospace) },
        text = {
            Column {
                Text(
                    "Enter the secure host URL (for example, jarvice.example.com) and its pairing token:",
                    color = TextSecondary,
                    fontSize = 13.sp
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = tempIp,
                    onValueChange = { tempIp = it },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = JarvisCyan,
                        unfocusedBorderColor = JarvisSurfaceBorder,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary
                    )
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = tempToken,
                    onValueChange = { tempToken = it },
                    label = { Text("Pairing token") },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = JarvisCyan,
                        unfocusedBorderColor = JarvisSurfaceBorder,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary
                    )
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onConnect(tempIp.trim(), tempToken.trim()) }, enabled = tempIp.isNotBlank() && tempToken.isNotBlank()) {
                Text("Connect", color = JarvisCyan)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = TextSecondary)
            }
        },
        containerColor = JarvisSurfaceDark
    )
}

@Composable
fun ChatBubble(msg: JarviceMessage) {
    val isUser = msg.sender == "USER"
    val bubbleColor = if (isUser) UserBubbleBg else AiBubbleBg
    val accent = if (isUser) JarvisBlue else JarvisCyan
    val time = formatTimestamp(msg.timestamp)

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 300.dp)
                .clip(
                    RoundedCornerShape(
                        topStart = 14.dp,
                        topEnd = 14.dp,
                        bottomStart = if (isUser) 14.dp else 3.dp,
                        bottomEnd = if (isUser) 3.dp else 14.dp
                    )
                )
                .background(bubbleColor)
                .border(
                    1.dp,
                    accent.copy(alpha = 0.4f),
                    RoundedCornerShape(
                        topStart = 14.dp,
                        topEnd = 14.dp,
                        bottomStart = if (isUser) 14.dp else 3.dp,
                        bottomEnd = if (isUser) 3.dp else 14.dp
                    )
                )
                .padding(horizontal = 12.dp, vertical = 9.dp)
        ) {
            Text(
                if (isUser) "YOU" else "JARVIS",
                color = accent,
                fontWeight = FontWeight.Bold,
                fontSize = 9.sp,
                fontFamily = FontFamily.Monospace,
                letterSpacing = 1.sp
            )
            Spacer(Modifier.height(3.dp))
            Text(msg.text, color = TextPrimary, fontSize = 14.sp, lineHeight = 19.sp)
            if (time.isNotEmpty()) {
                Spacer(Modifier.height(3.dp))
                Text(
                    time,
                    color = TextSecondary.copy(alpha = 0.7f),
                    fontSize = 9.sp,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.align(Alignment.End)
                )
            }
        }
    }
}

/** Extracts HH:mm from an ISO timestamp like "2026-08-04T22:06:40.014193". */
private fun formatTimestamp(ts: String): String {
    if (ts.length < 16 || !ts.contains("T")) return ""
    val timePart = ts.substringAfter("T")
    return if (timePart.length >= 5) timePart.substring(0, 5) else ""
}
