package com.example.myjarvice.ui.main

import android.app.Application
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
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
import com.example.myjarvice.data.PendingEmail
import com.example.myjarvice.data.SettingsStore
import com.example.myjarvice.theme.ArcGold
import com.example.myjarvice.theme.JarvisBlue
import com.example.myjarvice.theme.JarvisCyan
import com.example.myjarvice.theme.JarvisDarkBackground
import com.example.myjarvice.theme.JarvisSurfaceBorder
import com.example.myjarvice.theme.JarvisSurfaceDark
import com.example.myjarvice.theme.OfflineGray
import com.example.myjarvice.theme.OnlineGreen
import com.example.myjarvice.theme.TextPrimary
import com.example.myjarvice.theme.TextSecondary
import com.example.myjarvice.ui.JarvisArcReactor
import com.example.myjarvice.ui.voice.VoiceInfoDialog
import com.example.myjarvice.ui.voice.VoiceModeScreen
import com.example.myjarvice.ui.voice.VoicePickerDialog
import com.example.myjarvice.wake.WakeWordService

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun MainScreen(
    onOpenSettings: () -> Unit = {},
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

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val settings = SettingsStore(context.applicationContext)
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
    var showToolsMenu by remember { mutableStateOf(false) }

    if (showIpDialog) {
        ServerConfigDialog(
            currentIp = serverIp,
            currentToken = serverToken,
            onConnect = { ip, token ->
                viewModel.updateServerConnection(ip, token)
                Toast.makeText(context, "Connecting to $ip...", Toast.LENGTH_SHORT).show()
                showIpDialog = false
            },
            onDismiss = { showIpDialog = false }
        )
    }

    val currentPendingEmail = pendingEmail
    if (currentPendingEmail != null) {
        EmailApprovalDialog(
            draft = currentPendingEmail,
            onApprove = { viewModel.resolvePendingEmail(currentPendingEmail.id, approved = true) },
            onDiscard = { viewModel.resolvePendingEmail(currentPendingEmail.id, approved = false) }
        )
    }

    val currentPendingAction = pendingAction
    if (currentPendingAction != null) {
        AlertDialog(
            onDismissRequest = { viewModel.resolvePendingAction(false) },
            title = { Text("Confirm Phone Action", color = JarvisCyan) },
            text = { Text("Allow JARVIS to ${currentPendingAction.type.lowercase().replace('_', ' ')}: ${currentPendingAction.query}?", color = TextPrimary) },
            confirmButton = { TextButton(onClick = { viewModel.resolvePendingAction(true) }) { Text("Allow", color = JarvisCyan) } },
            dismissButton = { TextButton(onClick = { viewModel.resolvePendingAction(false) }) { Text("Deny", color = TextSecondary) } },
            containerColor = JarvisSurfaceDark
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

    val screenBg = Brush.verticalGradient(listOf(Color(0xFF0D121D), Color(0xFF060910)))

    Box(modifier = modifier.fillMaxSize()) {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            containerColor = JarvisDarkBackground,
            topBar = {
                ChatTopBar(
                    serverIp = serverIp,
                    connectionStatus = connectionStatus,
                    onOpenSettings = onOpenSettings,
                    onStatusClick = { showIpDialog = true },
                    onNewChat = { viewModel.clearChat() },
                    onVoiceMode = { viewModel.enterVoiceMode() }
                )
            }
        ) { innerPadding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(screenBg)
                    .padding(innerPadding)
                    .imePadding()
            ) {
                // Main Content Area: Hero State OR Active Chat Feed
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                ) {
                    if (chatHistory.isEmpty()) {
                        EmptyChatHero(
                            onPromptSelected = { prompt ->
                                viewModel.sendQuery(prompt)
                            }
                        )
                    } else {
                        ChatFeed(
                            chatHistory = chatHistory,
                            isThinking = isThinking,
                            onCopy = { text ->
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                clipboard.setPrimaryClip(ClipData.newPlainText("JARVIS", text))
                                Toast.makeText(context, "Copied to clipboard", Toast.LENGTH_SHORT).show()
                            },
                            onSpeak = { text ->
                                if (isSpeaking) viewModel.stopSpeaking() else viewModel.speak(text)
                            },
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }

                // Floating ChatGPT-style Bottom Bar
                FloatingInputBar(
                    textInput = textInput,
                    onTextChange = { textInput = it },
                    isListening = isListening,
                    showToolsMenu = showToolsMenu,
                    onToggleToolsMenu = { showToolsMenu = !showToolsMenu },
                    onToolSelected = { toolPrompt ->
                        showToolsMenu = false
                        viewModel.sendQuery(toolPrompt)
                    },
                    onSend = {
                        if (textInput.isNotBlank()) {
                            viewModel.sendQuery(textInput)
                            textInput = ""
                        }
                    },
                    onQuickVoice = { viewModel.toggleVoiceInput() },
                    onVoiceMode = { viewModel.enterVoiceMode() }
                )
            }
        }

        // Fullscreen ChatGPT-style Arc Reactor Voice Mode
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

/**
 * Top App Bar (ChatGPT mobile layout)
 */
@Composable
private fun ChatTopBar(
    serverIp: String,
    connectionStatus: ConnectionStatus,
    onOpenSettings: () -> Unit,
    onStatusClick: () -> Unit,
    onNewChat: () -> Unit,
    onVoiceMode: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF0D121D))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        // Left: Settings Drawer Icon
        IconButton(
            onClick = onOpenSettings,
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(JarvisSurfaceDark.copy(alpha = 0.6f))
        ) {
            Text("⚙️", fontSize = 17.sp)
        }

        // Center: Model Selector Dropdown Pill
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(20.dp))
                .background(JarvisSurfaceDark)
                .border(1.dp, JarvisSurfaceBorder, RoundedCornerShape(20.dp))
                .clickable { onStatusClick() }
                .padding(horizontal = 14.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val (dotColor, statusTooltip) = when (connectionStatus) {
                ConnectionStatus.CONNECTED -> OnlineGreen to "Online"
                ConnectionStatus.CONNECTING -> ArcGold to "Connecting"
                ConnectionStatus.DISCONNECTED -> OfflineGray to "Offline"
                ConnectionStatus.ERROR -> Color(0xFFFF5A5A) to "Error"
            }

            Box(
                modifier = Modifier
                    .size(7.dp)
                    .clip(CircleShape)
                    .background(dotColor)
            )
            Spacer(Modifier.width(8.dp))
            Text(
                "JARVIS 4.0",
                color = TextPrimary,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.SansSerif
            )
            Spacer(Modifier.width(4.dp))
            Text("▾", color = TextSecondary, fontSize = 12.sp)
        }

        // Right Action Icons: New Chat & Voice Mode
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onNewChat,
                modifier = Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(JarvisSurfaceDark.copy(alpha = 0.6f))
            ) {
                Text("➕", fontSize = 13.sp, color = JarvisCyan)
            }

            IconButton(
                onClick = onVoiceMode,
                modifier = Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(JarvisCyan.copy(alpha = 0.15f))
                    .border(1.dp, JarvisCyan.copy(alpha = 0.4f), CircleShape)
            ) {
                Text("🎧", fontSize = 16.sp)
            }
        }
    }
}

/**
 * Empty Chat State Hero (ChatGPT style with central pulsing Arc Reactor)
 */
@Composable
private fun EmptyChatHero(
    onPromptSelected: (String) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // Central Arc Reactor Hero
        JarvisArcReactor(
            size = 110.dp,
            isListening = false,
            isSpeaking = false
        )

        Spacer(Modifier.height(20.dp))

        Text(
            "What can I help with today, Sir?",
            color = TextPrimary,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )

        Spacer(Modifier.height(6.dp))

        Text(
            "JARVIS Mark VII Neural Core • Gemma 4 7.5B",
            color = TextSecondary,
            fontSize = 12.sp,
            fontFamily = FontFamily.Monospace,
            textAlign = TextAlign.Center
        )

        Spacer(Modifier.height(28.dp))

        // Quick Suggestion Cards in 2x2 Grid
        val promptCards = listOf(
            Triple("⚡ System Diagnostics", "Check battery, memory & network status", "Jarvis, run a complete device health and battery check."),
            Triple("📁 Drive RAG Engine", "Search documents across D: and E: drives", "Jarvis, search my local indexed files for recent project documents."),
            Triple("✉️ Compose Email", "Draft a quick status update email", "Jarvis, draft a polite status update email to the team."),
            Triple("💡 CS & AI Insights", "Analyze algorithms & architectures", "Jarvis, explain modern transformer multi-head attention simply.")
        )

        Column(
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            promptCards.chunked(2).forEach { rowItems ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    rowItems.forEach { (title, subtitle, prompt) ->
                        PromptSuggestionCard(
                            title = title,
                            subtitle = subtitle,
                            onClick = { onPromptSelected(prompt) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PromptSuggestionCard(
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(Color(0xFF141A26))
            .border(1.dp, Color(0xFF222D3E), RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp)
    ) {
        Text(
            title,
            color = JarvisCyan,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(Modifier.height(4.dp))
        Text(
            subtitle,
            color = TextSecondary,
            fontSize = 11.sp,
            lineHeight = 15.sp
        )
    }
}

/**
 * Message Feed (ChatGPT stream layout)
 */
@Composable
private fun ChatFeed(
    chatHistory: List<JarviceMessage>,
    isThinking: Boolean,
    onCopy: (String) -> Unit,
    onSpeak: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val listState = rememberLazyListState()

    LaunchedEffect(chatHistory.size, isThinking) {
        if (chatHistory.isNotEmpty()) {
            listState.animateScrollToItem(chatHistory.size - 1)
        }
    }

    LazyColumn(
        state = listState,
        modifier = modifier.padding(horizontal = 14.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        items(chatHistory) { msg ->
            if (msg.sender == "USER") {
                UserMessageBubble(msg = msg)
            } else {
                JarvisMessageBubble(
                    msg = msg,
                    onCopy = { onCopy(msg.text) },
                    onSpeak = { onSpeak(msg.text) }
                )
            }
        }

        if (isThinking) {
            item {
                ThinkingIndicatorBubble()
            }
        }
    }
}

/**
 * User Chat Bubble (ChatGPT style anchored on right)
 */
@Composable
private fun UserMessageBubble(msg: JarviceMessage) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.End
    ) {
        Box(
            modifier = Modifier
                .widthIn(max = 290.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(Color(0xFF1C2433))
                .border(1.dp, Color(0xFF2E3D54), RoundedCornerShape(18.dp))
                .padding(horizontal = 14.dp, vertical = 10.dp)
        ) {
            Text(
                msg.text,
                color = Color.White,
                fontSize = 14.sp,
                lineHeight = 20.sp
            )
        }
    }
}

/**
 * JARVIS Chat Bubble (ChatGPT style with Arc Reactor Avatar & Action Toolbar)
 */
@Composable
private fun JarvisMessageBubble(
    msg: JarviceMessage,
    onCopy: () -> Unit,
    onSpeak: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Start
    ) {
        // Mini Arc Reactor Avatar
        Box(
            modifier = Modifier
                .padding(top = 2.dp)
                .size(30.dp)
                .clip(CircleShape)
                .background(Color(0xFF111824))
                .border(1.dp, JarvisCyan.copy(alpha = 0.5f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            JarvisArcReactor(size = 22.dp)
        }

        Spacer(Modifier.width(10.dp))

        Column(modifier = Modifier.weight(1f)) {
            // Header Label
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    "JARVIS",
                    color = JarvisCyan,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    letterSpacing = 1.sp
                )
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(Color(0xFF162030))
                        .padding(horizontal = 5.dp, vertical = 1.dp)
                ) {
                    Text("Gemma 4", color = TextSecondary, fontSize = 9.sp, fontFamily = FontFamily.Monospace)
                }
            }

            Spacer(Modifier.height(4.dp))

            // Body text
            Text(
                msg.text,
                color = TextPrimary,
                fontSize = 14.sp,
                lineHeight = 21.sp
            )

            Spacer(Modifier.height(6.dp))

            // Action Toolbar (Copy, Speak, Timestamp)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Row(
                    modifier = Modifier.clickable(onClick = onCopy),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("📋", fontSize = 11.sp)
                    Spacer(Modifier.width(4.dp))
                    Text("Copy", color = TextSecondary, fontSize = 10.sp)
                }

                Row(
                    modifier = Modifier.clickable(onClick = onSpeak),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("🔊", fontSize = 11.sp)
                    Spacer(Modifier.width(4.dp))
                    Text("Listen", color = TextSecondary, fontSize = 10.sp)
                }

                val time = formatTimestamp(msg.timestamp)
                if (time.isNotBlank()) {
                    Text(
                        time,
                        color = TextSecondary.copy(alpha = 0.5f),
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
        }
    }
}

/**
 * Thinking Bubble (ChatGPT-style streaming indicator)
 */
@Composable
private fun ThinkingIndicatorBubble() {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Start,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(30.dp)
                .clip(CircleShape)
                .background(Color(0xFF111824)),
            contentAlignment = Alignment.Center
        ) {
            JarvisArcReactor(size = 22.dp, isSpeaking = true)
        }

        Spacer(Modifier.width(10.dp))

        val transition = rememberInfiniteTransition(label = "thinking")
        val alpha by transition.animateFloat(
            initialValue = 0.3f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(700, easing = LinearEasing), RepeatMode.Reverse),
            label = "thinkingAlpha"
        )

        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(14.dp))
                .background(Color(0xFF131A26))
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "Thinking...",
                color = JarvisCyan.copy(alpha = alpha),
                fontSize = 13.sp,
                fontFamily = FontFamily.Monospace
            )
        }
    }
}

/**
 * Floating Bottom Input Bar (ChatGPT mobile design)
 */
@Composable
private fun FloatingInputBar(
    textInput: String,
    onTextChange: (String) -> Unit,
    isListening: Boolean,
    showToolsMenu: Boolean,
    onToggleToolsMenu: () -> Unit,
    onToolSelected: (String) -> Unit,
    onSend: () -> Unit,
    onQuickVoice: () -> Unit,
    onVoiceMode: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 10.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(28.dp))
                .background(Color(0xFF141924))
                .border(1.dp, Color(0xFF242E3F), RoundedCornerShape(28.dp))
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // (+) Tool Attachment Button
            Box {
                IconButton(
                    onClick = onToggleToolsMenu,
                    modifier = Modifier.size(38.dp)
                ) {
                    Text("➕", fontSize = 14.sp, color = JarvisCyan)
                }

                DropdownMenu(
                    expanded = showToolsMenu,
                    onDismissRequest = onToggleToolsMenu,
                    modifier = Modifier.background(JarvisSurfaceDark)
                ) {
                    DropdownMenuItem(
                        text = { Text("📁 Search Local Drive Docs", color = TextPrimary, fontSize = 13.sp) },
                        onClick = { onToolSelected("Jarvis, search indexed documents on my PC drives.") }
                    )
                    DropdownMenuItem(
                        text = { Text("⚡ Check Device Health", color = TextPrimary, fontSize = 13.sp) },
                        onClick = { onToolSelected("Jarvis, give me a full battery and performance diagnostics.") }
                    )
                    DropdownMenuItem(
                        text = { Text("📅 Today's Calendar Agenda", color = TextPrimary, fontSize = 13.sp) },
                        onClick = { onToolSelected("Jarvis, what events are on my calendar today?") }
                    )
                }
            }

            // Text Input Field
            OutlinedTextField(
                value = textInput,
                onValueChange = onTextChange,
                placeholder = { Text("Ask JARVIS anything...", color = TextSecondary, fontSize = 14.sp) },
                modifier = Modifier.weight(1f),
                maxLines = 4,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Color.Transparent,
                    unfocusedBorderColor = Color.Transparent,
                    focusedTextColor = TextPrimary,
                    unfocusedTextColor = TextPrimary,
                    cursorColor = JarvisCyan,
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent
                )
            )

            // Right Action: Send Button OR Voice / Headphones
            if (textInput.isNotBlank()) {
                IconButton(
                    onClick = onSend,
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(JarvisCyan)
                ) {
                    Text("⬆", color = Color.Black, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                }
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = onQuickVoice,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Text(if (isListening) "🎙️" else "🎤", fontSize = 16.sp)
                    }

                    IconButton(
                        onClick = onVoiceMode,
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF1C2432))
                    ) {
                        Text("🎧", fontSize = 15.sp)
                    }
                }
            }
        }
    }
}

/**
 * Server Configuration Dialog with Quick Presets
 */
@Composable
private fun ServerConfigDialog(
    currentIp: String,
    currentToken: String,
    onConnect: (String, String) -> Unit,
    onDismiss: () -> Unit
) {
    var tempIp by remember { mutableStateOf(if (currentIp.isBlank()) "127.0.0.1:8000" else currentIp) }
    var tempToken by remember { mutableStateOf(if (currentToken.isBlank()) "jarvis_local_token" else currentToken) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("HOST CONNECTION", color = JarvisCyan, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold) },
        text = {
            Column {
                Text(
                    "Select a quick network preset or enter your PC server address:",
                    color = TextSecondary,
                    fontSize = 12.sp
                )
                Spacer(Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Button(
                        onClick = { tempIp = "127.0.0.1:8000"; tempToken = "jarvis_local_token" },
                        colors = ButtonDefaults.buttonColors(containerColor = JarvisCyan.copy(alpha = 0.2f)),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("⚡ USB", color = JarvisCyan, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                    Button(
                        onClick = { tempIp = "192.168.1.37:8000"; tempToken = "jarvis_local_token" },
                        colors = ButtonDefaults.buttonColors(containerColor = JarvisBlue.copy(alpha = 0.2f)),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("📶 Wi-Fi", color = JarvisBlue, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                    Button(
                        onClick = { tempIp = "192.168.137.1:8000"; tempToken = "jarvis_local_token" },
                        colors = ButtonDefaults.buttonColors(containerColor = ArcGold.copy(alpha = 0.2f)),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("📱 Hotspot", color = ArcGold, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }
                }

                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = tempIp,
                    onValueChange = { tempIp = it },
                    label = { Text("Server Host / IP", color = TextSecondary, fontSize = 12.sp) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = JarvisCyan,
                        unfocusedBorderColor = JarvisSurfaceBorder,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = tempToken,
                    onValueChange = { tempToken = it },
                    label = { Text("Pairing Token", color = TextSecondary, fontSize = 12.sp) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = JarvisCyan,
                        unfocusedBorderColor = JarvisSurfaceBorder,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val effectiveToken = if (tempToken.isBlank()) "jarvis_local_token" else tempToken.trim()
                    onConnect(tempIp.trim(), effectiveToken)
                },
                enabled = tempIp.isNotBlank()
            ) {
                Text("CONNECT", color = JarvisCyan, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("CANCEL", color = TextSecondary)
            }
        },
        containerColor = JarvisSurfaceDark
    )
}

private fun shareTranscript(context: Context, transcript: String) {

    if (transcript.isBlank()) {
        Toast.makeText(context, "Nothing to share yet.", Toast.LENGTH_SHORT).show()
        return
    }
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_SUBJECT, "JARVIS conversation")
        putExtra(Intent.EXTRA_TEXT, transcript)
    }
    context.startActivity(Intent.createChooser(send, "Share transcript"))
}

private fun formatTimestamp(ts: String): String {
    if (ts.length < 16 || !ts.contains("T")) return ""
    val timePart = ts.substringAfter("T")
    return if (timePart.length >= 5) timePart.substring(0, 5) else ""
}
