package com.example.myjarvice.ui.main

import android.app.Application
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Base64
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.myjarvice.data.ChatSession
import com.example.myjarvice.data.ConnectionStatus
import com.example.myjarvice.data.JarvisMessage
import com.example.myjarvice.data.PendingEmail
import com.example.myjarvice.data.SettingsStore
import com.example.myjarvice.data.WebSource
import com.example.myjarvice.theme.ArcGold
import com.example.myjarvice.theme.OfflineGray
import com.example.myjarvice.theme.OnlineGreen
import com.example.myjarvice.ui.JarvisArcReactor
import com.example.myjarvice.ui.icons.IconActivity
import com.example.myjarvice.ui.icons.IconCopy
import com.example.myjarvice.ui.icons.IconDocument
import com.example.myjarvice.ui.icons.IconMenu
import com.example.myjarvice.ui.icons.IconMessage
import com.example.myjarvice.ui.icons.IconMicrophone
import com.example.myjarvice.ui.icons.IconNewChat
import com.example.myjarvice.ui.icons.IconPlus
import com.example.myjarvice.ui.icons.IconSend
import com.example.myjarvice.ui.icons.IconSettings
import com.example.myjarvice.ui.icons.IconSparkles
import com.example.myjarvice.ui.icons.IconSpeaker
import com.example.myjarvice.ui.icons.IconTrash
import com.example.myjarvice.ui.icons.IconVoiceWaveform
import com.example.myjarvice.ui.voice.VoiceInfoDialog
import com.example.myjarvice.ui.voice.VoiceModeScreen
import com.example.myjarvice.ui.voice.VoicePickerDialog
import com.example.myjarvice.wake.WakeWordService
import kotlinx.coroutines.launch
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.Calendar

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun MainScreen(
    onOpenSettings: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scheme = MaterialTheme.colorScheme

    val viewModel: MainScreenViewModel = viewModel {
        MainScreenViewModel(context.applicationContext as Application)
    }

    val connectionStatus by viewModel.connectionStatus.collectAsStateWithLifecycle()
    val chatHistory by viewModel.chatHistory.collectAsStateWithLifecycle()
    val savedSessions by viewModel.savedSessions.collectAsStateWithLifecycle()
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

    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val coroutineScope = rememberCoroutineScope()

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

    // File Attachment State
    var attachedFileName by remember { mutableStateOf<String?>(null) }
    var attachedFileContent by remember { mutableStateOf<String?>(null) }

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            try {
                var fileName = "Document"
                context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                    val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (cursor.moveToFirst() && nameIndex >= 0) {
                        fileName = cursor.getString(nameIndex)
                    }
                }
                attachedFileName = fileName

                val inputStream = context.contentResolver.openInputStream(uri)
                val reader = BufferedReader(InputStreamReader(inputStream))
                val content = reader.readText()
                reader.close()
                attachedFileContent = if (content.length > 8000) content.take(8000) + "\n...[Truncated]" else content

                Toast.makeText(context, "Attached: $fileName", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(context, "Attached file: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

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
            title = { Text("Confirm Device Action", color = scheme.onSurface, fontWeight = FontWeight.SemiBold) },
            text = { Text("Allow JARVIS to ${currentPendingAction.type.lowercase().replace('_', ' ')}: ${currentPendingAction.query}?", color = scheme.onSurfaceVariant) },
            confirmButton = { TextButton(onClick = { viewModel.resolvePendingAction(true) }) { Text("Allow", color = scheme.primary, fontWeight = FontWeight.SemiBold) } },
            dismissButton = { TextButton(onClick = { viewModel.resolvePendingAction(false) }) { Text("Deny", color = scheme.onSurfaceVariant) } },
            containerColor = scheme.surface
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

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet(
                drawerContainerColor = scheme.surface,
                drawerShape = RoundedCornerShape(topEnd = 16.dp, bottomEnd = 16.dp),
                modifier = Modifier.width(300.dp)
            ) {
                HistoryDrawerContent(
                    savedSessions = savedSessions,
                    onNewChat = {
                        coroutineScope.launch { drawerState.close() }
                        viewModel.startNewChat()
                    },
                    onSelectSession = { session ->
                        coroutineScope.launch { drawerState.close() }
                        viewModel.loadSession(session)
                    },
                    onDeleteSession = { sessionId ->
                        viewModel.deleteSession(sessionId)
                    },
                    onClearAllHistory = {
                        viewModel.clearAllHistory()
                    },
                    onOpenSettings = {
                        coroutineScope.launch { drawerState.close() }
                        onOpenSettings()
                    }
                )
            }
        }
    ) {
        Box(modifier = modifier.fillMaxSize().background(scheme.background)) {
            Scaffold(
                modifier = Modifier.fillMaxSize(),
                containerColor = scheme.background,
                topBar = {
                    ChatTopBar(
                        connectionStatus = connectionStatus,
                        onOpenDrawer = { coroutineScope.launch { drawerState.open() } },
                        onStatusClick = { showIpDialog = true },
                        onNewChat = { viewModel.startNewChat() },
                        onVoiceMode = { viewModel.enterVoiceMode() }
                    )
                }
            ) { innerPadding ->
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                        .imePadding()
                ) {
                    // Content Area: Empty Hero OR Active Chat Feed
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

                    // Attached File Indicator Chip
                    if (attachedFileName != null) {
                        Row(
                            modifier = Modifier
                                .padding(horizontal = 16.dp, vertical = 4.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(scheme.surfaceVariant)
                                .border(1.dp, scheme.primary.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconDocument(tint = scheme.primary, size = 14.dp)
                            Spacer(Modifier.width(8.dp))
                            Text(
                                attachedFileName ?: "",
                                color = scheme.onSurface,
                                fontSize = 12.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                "✕",
                                color = scheme.onSurfaceVariant,
                                fontSize = 14.sp,
                                modifier = Modifier.clickable {
                                    attachedFileName = null
                                    attachedFileContent = null
                                }
                            )
                        }
                    }

                    // Floating Bottom Input Bar
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
                        onAttachFile = {
                            showToolsMenu = false
                            filePickerLauncher.launch("*/*")
                        },
                        onSend = {
                            if (textInput.isNotBlank() || attachedFileContent != null) {
                                val fullQuery = if (attachedFileName != null) {
                                    "[Attached File: $attachedFileName]\n${attachedFileContent.orEmpty()}\n\n$textInput"
                                } else {
                                    textInput
                                }
                                viewModel.sendQuery(fullQuery)
                                textInput = ""
                                attachedFileName = null
                                attachedFileContent = null
                            }
                        },
                        onQuickVoice = { viewModel.toggleVoiceInput() },
                        onVoiceMode = { viewModel.enterVoiceMode() }
                    )
                }
            }

            // Fullscreen Hands-free Voice Mode
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
}

/**
 * Top App Bar (JARVIS 1.0)
 */
@Composable
private fun ChatTopBar(
    connectionStatus: ConnectionStatus,
    onOpenDrawer: () -> Unit,
    onStatusClick: () -> Unit,
    onNewChat: () -> Unit,
    onVoiceMode: () -> Unit
) {
    val scheme = MaterialTheme.colorScheme

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .background(scheme.background)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        // Left: Menu Icon
        IconButton(
            onClick = onOpenDrawer,
            modifier = Modifier.size(40.dp)
        ) {
            IconMenu(tint = scheme.onSurfaceVariant, size = 20.dp)
        }

        // Center: Model Selector Chip
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(20.dp))
                .background(scheme.surface)
                .border(1.dp, scheme.outline.copy(alpha = 0.35f), RoundedCornerShape(20.dp))
                .clickable { onStatusClick() }
                .padding(horizontal = 14.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val dotColor = when (connectionStatus) {
                ConnectionStatus.CONNECTED -> OnlineGreen
                ConnectionStatus.CONNECTING -> ArcGold
                ConnectionStatus.DISCONNECTED -> OfflineGray
                ConnectionStatus.ERROR -> Color(0xFFEF4444)
            }

            Box(
                modifier = Modifier
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(dotColor)
            )
            Spacer(Modifier.width(8.dp))
            Text(
                "JARVIS",
                color = scheme.onSurface,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.width(4.dp))
            Text(
                "1.0",
                color = scheme.onSurfaceVariant,
                fontSize = 12.sp,
                fontWeight = FontWeight.Normal
            )
        }

        // Right Actions: New Chat & Voice Mode
        Row(
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onNewChat,
                modifier = Modifier.size(38.dp)
            ) {
                IconNewChat(tint = scheme.onSurfaceVariant, size = 20.dp)
            }

            IconButton(
                onClick = onVoiceMode,
                modifier = Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(scheme.surfaceVariant)
            ) {
                IconVoiceWaveform(tint = scheme.primary, size = 18.dp)
            }
        }
    }
}

/**
 * Sidebar Navigation Drawer (Chat History)
 */
@Composable
private fun HistoryDrawerContent(
    savedSessions: List<ChatSession>,
    onNewChat: () -> Unit,
    onSelectSession: (ChatSession) -> Unit,
    onDeleteSession: (String) -> Unit,
    onClearAllHistory: () -> Unit,
    onOpenSettings: () -> Unit
) {
    val scheme = MaterialTheme.colorScheme

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // "+ New chat" Button
        Button(
            onClick = onNewChat,
            colors = ButtonDefaults.buttonColors(containerColor = scheme.surfaceVariant),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, scheme.outline.copy(alpha = 0.35f), RoundedCornerShape(12.dp))
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Start,
                modifier = Modifier.fillMaxWidth()
            ) {
                IconPlus(tint = scheme.onSurface, size = 16.dp)
                Spacer(Modifier.width(10.dp))
                Text(
                    "New chat",
                    color = scheme.onSurface,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }

        Spacer(Modifier.height(18.dp))

        Text(
            "Recent",
            color = scheme.onSurfaceVariant,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        )

        if (savedSessions.isEmpty()) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "No conversation history",
                    color = scheme.onSurfaceVariant.copy(alpha = 0.6f),
                    fontSize = 13.sp
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                items(savedSessions, key = { it.id }) { session ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .clickable { onSelectSession(session) }
                            .padding(horizontal = 10.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            IconMessage(tint = scheme.onSurfaceVariant, size = 16.dp)
                            Spacer(Modifier.width(10.dp))
                            Text(
                                session.title,
                                color = scheme.onSurface,
                                fontSize = 13.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        IconButton(
                            onClick = { onDeleteSession(session.id) },
                            modifier = Modifier.size(28.dp)
                        ) {
                            IconTrash(tint = scheme.onSurfaceVariant, size = 14.dp)
                        }
                    }
                }
            }
        }

        HorizontalDivider(color = scheme.outline.copy(alpha = 0.25f), thickness = 1.dp)
        Spacer(Modifier.height(10.dp))

        // Bottom Actions: Settings & Clear History
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .clickable { onOpenSettings() }
                .padding(horizontal = 10.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconSettings(tint = scheme.onSurfaceVariant, size = 18.dp)
            Spacer(Modifier.width(12.dp))
            Text("Settings", color = scheme.onSurface, fontSize = 13.sp, fontWeight = FontWeight.Medium)
        }

        if (savedSessions.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .clickable { onClearAllHistory() }
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconTrash(tint = Color(0xFFEF4444), size = 16.dp)
                Spacer(Modifier.width(12.dp))
                Text("Clear conversations", color = Color(0xFFEF4444), fontSize = 13.sp)
            }
        }
    }
}

/**
 * Empty Chat State Hero (JARVIS 1.0 with PC Automation & Web Search Shortcuts)
 */
@Composable
private fun EmptyChatHero(
    onPromptSelected: (String) -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    val greeting = remember {
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        when {
            hour < 12 -> "Good morning, Sir"
            hour < 18 -> "Good afternoon, Sir"
            else -> "Good evening, Sir"
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        JarvisArcReactor(
            size = 72.dp,
            isListening = false,
            isSpeaking = false
        )

        Spacer(Modifier.height(24.dp))

        Text(
            greeting,
            color = scheme.onBackground,
            fontSize = 24.sp,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center
        )

        Spacer(Modifier.height(6.dp))

        Text(
            "How can I assist your workstation, web queries, and workflow today?",
            color = scheme.onSurfaceVariant,
            fontSize = 14.sp,
            textAlign = TextAlign.Center
        )

        Spacer(Modifier.height(28.dp))

        // Professional 2x2 Suggestion Cards
        val promptCards = listOf(
            PromptCardItem(
                title = "Live Web & News",
                desc = "Real-time AI headlines & web lookup",
                prompt = "Jarvis, what is the latest AI technology news today?",
                icon = { IconSparkles(tint = Color(0xFF38BDF8), size = 18.dp) }
            ),
            PromptCardItem(
                title = "Live Weather Forecast",
                desc = "Instant city weather & climate",
                prompt = "Jarvis, what is the live weather forecast for Pune today?",
                icon = { IconActivity(tint = ArcGold, size = 18.dp) }
            ),
            PromptCardItem(
                title = "Host PC Screen",
                desc = "Live desktop screenshot capture",
                prompt = "Jarvis, capture host PC screenshot.",
                icon = { IconDocument(tint = scheme.primary, size = 18.dp) }
            ),
            PromptCardItem(
                title = "Codebase & Doc RAG",
                desc = "Search indexed project files",
                prompt = "Jarvis, where in the project do we handle device actions like camera and maps?",
                icon = { IconDocument(tint = Color(0xFF34D399), size = 18.dp) }
            )
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
                    rowItems.forEach { item ->
                        PromptSuggestionCard(
                            item = item,
                            onClick = { onPromptSelected(item.prompt) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }
    }
}

private data class PromptCardItem(
    val title: String,
    val desc: String,
    val prompt: String,
    val icon: @Composable () -> Unit
)

@Composable
private fun PromptSuggestionCard(
    item: PromptCardItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scheme = MaterialTheme.colorScheme

    Column(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(scheme.surface)
            .border(1.dp, scheme.outline.copy(alpha = 0.35f), RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(14.dp)
    ) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(scheme.surfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            item.icon()
        }

        Spacer(Modifier.height(10.dp))

        Text(
            item.title,
            color = scheme.onSurface,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(Modifier.height(2.dp))
        Text(
            item.desc,
            color = scheme.onSurfaceVariant,
            fontSize = 11.sp,
            lineHeight = 15.sp,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/**
 * Message Feed (Adaptive Theme with Live Web Sources & Screenshot Cards)
 */
@Composable
private fun ChatFeed(
    chatHistory: List<JarvisMessage>,
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
        modifier = modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
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
                ThinkingIndicator()
            }
        }
    }
}

@Composable
private fun UserMessageBubble(msg: JarvisMessage) {
    val scheme = MaterialTheme.colorScheme

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.End
    ) {
        Box(
            modifier = Modifier
                .widthIn(max = 290.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(scheme.surfaceVariant)
                .padding(horizontal = 14.dp, vertical = 10.dp)
        ) {
            Text(
                msg.text,
                color = scheme.onSurface,
                fontSize = 14.sp,
                lineHeight = 20.sp
            )
        }
    }
}

@Composable
private fun JarvisMessageBubble(
    msg: JarvisMessage,
    onCopy: () -> Unit,
    onSpeak: () -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    val context = LocalContext.current
    var showFullscreenImage by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Start
    ) {
        // Mini Avatar
        Box(
            modifier = Modifier
                .padding(top = 2.dp)
                .size(24.dp)
                .clip(CircleShape)
                .background(scheme.surfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            JarvisArcReactor(size = 18.dp)
        }

        Spacer(Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            // Header
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    "JARVIS",
                    color = scheme.onSurface,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    "1.0",
                    color = scheme.onSurfaceVariant,
                    fontSize = 11.sp
                )
            }

            Spacer(Modifier.height(4.dp))

            // Body text
            Text(
                msg.text,
                color = scheme.onSurface,
                fontSize = 14.sp,
                lineHeight = 22.sp
            )

            // Desktop Screenshot / Image Rendering
            if (!msg.image.isNullOrBlank()) {
                val bitmap: Bitmap? = remember(msg.image) {
                    try {
                        val rawBase64 = msg.image.substringAfter("base64,")
                        val bytes = Base64.decode(rawBase64, Base64.DEFAULT)
                        BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                    } catch (e: Exception) {
                        null
                    }
                }

                if (bitmap != null) {
                    Spacer(Modifier.height(10.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(scheme.surfaceVariant)
                            .border(1.dp, scheme.primary.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                            .clickable { showFullscreenImage = true }
                            .padding(6.dp)
                    ) {
                        Column {
                            Image(
                                bitmap = bitmap.asImageBitmap(),
                                contentDescription = "Host PC Screenshot",
                                contentScale = ContentScale.FillWidth,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                            )
                            Spacer(Modifier.height(6.dp))
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    "🖥️ Host PC Screen • Tap to expand",
                                    color = scheme.primary,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }

                    if (showFullscreenImage) {
                        FullscreenImageDialog(
                            bitmap = bitmap,
                            onDismiss = { showFullscreenImage = false }
                        )
                    }
                }
            }

            // Live Web Sources Row (Clickable citation chips)
            if (msg.sources.isNotEmpty()) {
                Spacer(Modifier.height(10.dp))
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(scheme.surfaceVariant.copy(alpha = 0.5f))
                        .border(1.dp, scheme.outline.copy(alpha = 0.25f), RoundedCornerShape(10.dp))
                        .padding(horizontal = 10.dp, vertical = 8.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "🌐 Sources & Live Grounding",
                            color = scheme.primary,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    Spacer(Modifier.height(6.dp))
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        items(msg.sources) { src ->
                            Row(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(scheme.surface)
                                    .border(1.dp, scheme.primary.copy(alpha = 0.35f), RoundedCornerShape(8.dp))
                                    .clickable {
                                        if (src.url.isNotBlank()) {
                                            try {
                                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(src.url))
                                                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                                context.startActivity(intent)
                                            } catch (e: Exception) {
                                                Toast.makeText(context, "Could not open source link", Toast.LENGTH_SHORT).show()
                                            }
                                        }
                                    }
                                    .padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    src.domain.ifBlank { "source" },
                                    color = scheme.onSurface,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Medium
                                )
                                Spacer(Modifier.width(4.dp))
                                Text("↗", color = scheme.primary, fontSize = 10.sp)
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(8.dp))

            // Action Toolbar (Copy, Speak, Timestamp)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .clickable(onClick = onCopy)
                        .padding(horizontal = 4.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconCopy(tint = scheme.onSurfaceVariant, size = 14.dp)
                    Spacer(Modifier.width(4.dp))
                    Text("Copy", color = scheme.onSurfaceVariant, fontSize = 11.sp)
                }

                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .clickable(onClick = onSpeak)
                        .padding(horizontal = 4.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconSpeaker(tint = scheme.onSurfaceVariant, size = 14.dp)
                    Spacer(Modifier.width(4.dp))
                    Text("Listen", color = scheme.onSurfaceVariant, fontSize = 11.sp)
                }

                val time = formatTimestamp(msg.timestamp)
                if (time.isNotBlank()) {
                    Text(
                        time,
                        color = scheme.onSurfaceVariant.copy(alpha = 0.6f),
                        fontSize = 11.sp
                    )
                }
            }
        }
    }
}

/**
 * Fullscreen Interactive Image Dialog
 */
@Composable
private fun FullscreenImageDialog(
    bitmap: Bitmap,
    onDismiss: () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.95f))
        ) {
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = "Fullscreen Host Screenshot",
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                contentScale = ContentScale.Fit
            )

            // Top Bar with Close Action
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Host PC Display Capture",
                    color = Color.White,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold
                )

                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.25f))
                ) {
                    Text("✕", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun ThinkingIndicator() {
    val scheme = MaterialTheme.colorScheme

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Start,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(24.dp)
                .clip(CircleShape)
                .background(scheme.surfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            JarvisArcReactor(size = 18.dp, isSpeaking = true)
        }

        Spacer(Modifier.width(12.dp))

        val transition = rememberInfiniteTransition(label = "thinking")
        val alpha by transition.animateFloat(
            initialValue = 0.3f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(600, easing = LinearEasing), RepeatMode.Reverse),
            label = "thinkingAlpha"
        )

        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(12.dp))
                .background(scheme.surfaceVariant)
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "Thinking...",
                color = scheme.primary.copy(alpha = alpha),
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

/**
 * Floating Bottom Input Bar (with Host PC & Web Search Tools)
 */
@Composable
private fun FloatingInputBar(
    textInput: String,
    onTextChange: (String) -> Unit,
    isListening: Boolean,
    showToolsMenu: Boolean,
    onToggleToolsMenu: () -> Unit,
    onToolSelected: (String) -> Unit,
    onAttachFile: () -> Unit,
    onSend: () -> Unit,
    onQuickVoice: () -> Unit,
    onVoiceMode: () -> Unit
) {
    val scheme = MaterialTheme.colorScheme

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 10.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(28.dp))
                .background(scheme.surface)
                .border(1.dp, scheme.outline.copy(alpha = 0.35f), RoundedCornerShape(28.dp))
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // (+) Tool Attachment
            Box {
                IconButton(
                    onClick = onToggleToolsMenu,
                    modifier = Modifier.size(38.dp)
                ) {
                    IconPlus(tint = scheme.onSurfaceVariant, size = 18.dp)
                }

                DropdownMenu(
                    expanded = showToolsMenu,
                    onDismissRequest = onToggleToolsMenu,
                    modifier = Modifier.background(scheme.surface)
                ) {
                    DropdownMenuItem(
                        text = { Text("Search Web & Live News", color = scheme.onSurface, fontSize = 13.sp) },
                        leadingIcon = { IconSparkles(tint = Color(0xFF38BDF8), size = 16.dp) },
                        onClick = { onToolSelected("Jarvis, search the web for the latest artificial intelligence news.") }
                    )
                    DropdownMenuItem(
                        text = { Text("Live Weather Forecast", color = scheme.onSurface, fontSize = 13.sp) },
                        leadingIcon = { IconActivity(tint = ArcGold, size = 16.dp) },
                        onClick = { onToolSelected("Jarvis, what is the live weather forecast for Pune today?") }
                    )
                    DropdownMenuItem(
                        text = { Text("Host PC Screenshot", color = scheme.onSurface, fontSize = 13.sp) },
                        leadingIcon = { IconDocument(tint = scheme.primary, size = 16.dp) },
                        onClick = { onToolSelected("Jarvis, capture host PC screenshot.") }
                    )
                    DropdownMenuItem(
                        text = { Text("Host PC Hardware Stats", color = scheme.onSurface, fontSize = 13.sp) },
                        leadingIcon = { IconActivity(tint = scheme.primary, size = 16.dp) },
                        onClick = { onToolSelected("Jarvis, what are my PC hardware stats (CPU, RAM, Disks)?") }
                    )
                    DropdownMenuItem(
                        text = { Text("Lock Host Workstation", color = scheme.onSurface, fontSize = 13.sp) },
                        leadingIcon = { IconSparkles(tint = ArcGold, size = 16.dp) },
                        onClick = { onToolSelected("Jarvis, lock my host PC workstation.") }
                    )
                    DropdownMenuItem(
                        text = { Text("Search PC Code & Docs", color = scheme.onSurface, fontSize = 13.sp) },
                        leadingIcon = { IconDocument(tint = Color(0xFF34D399), size = 16.dp) },
                        onClick = { onToolSelected("Jarvis, search indexed files on my PC drives.") }
                    )
                    DropdownMenuItem(
                        text = { Text("Attach Document / Code", color = scheme.onSurface, fontSize = 13.sp) },
                        leadingIcon = { IconDocument(tint = scheme.primary, size = 16.dp) },
                        onClick = onAttachFile
                    )
                }
            }

            // Text Input Field
            OutlinedTextField(
                value = textInput,
                onValueChange = onTextChange,
                placeholder = { Text("Ask JARVIS, search web, or control PC...", color = scheme.onSurfaceVariant, fontSize = 14.sp) },
                modifier = Modifier.weight(1f),
                maxLines = 4,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Color.Transparent,
                    unfocusedBorderColor = Color.Transparent,
                    focusedTextColor = scheme.onSurface,
                    unfocusedTextColor = scheme.onSurface,
                    cursorColor = scheme.primary,
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent
                )
            )

            // Right Action: Send Button OR Voice / Waveform
            if (textInput.isNotBlank()) {
                IconButton(
                    onClick = onSend,
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(scheme.primary)
                ) {
                    IconSend(tint = scheme.onPrimary, size = 16.dp)
                }
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = onQuickVoice,
                        modifier = Modifier.size(36.dp)
                    ) {
                        IconMicrophone(
                            tint = if (isListening) scheme.primary else scheme.onSurfaceVariant,
                            size = 18.dp
                        )
                    }

                    IconButton(
                        onClick = onVoiceMode,
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(scheme.surfaceVariant)
                    ) {
                        IconVoiceWaveform(tint = scheme.primary, size = 16.dp)
                    }
                }
            }
        }
    }
}

/**
 * Server Configuration Dialog
 */
@Composable
private fun ServerConfigDialog(
    currentIp: String,
    currentToken: String,
    onConnect: (String, String) -> Unit,
    onDismiss: () -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    var tempIp by remember { mutableStateOf(if (currentIp.isBlank()) "127.0.0.1:8000" else currentIp) }
    var tempToken by remember { mutableStateOf(if (currentToken.isBlank()) "jarvis_local_token" else currentToken) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Host Connection", color = scheme.onSurface, fontWeight = FontWeight.SemiBold) },
        text = {
            Column {
                Text(
                    "Select a network preset or specify host address:",
                    color = scheme.onSurfaceVariant,
                    fontSize = 12.sp
                )
                Spacer(Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Button(
                        onClick = { tempIp = "127.0.0.1:8000"; tempToken = "jarvis_local_token" },
                        colors = ButtonDefaults.buttonColors(containerColor = scheme.surfaceVariant),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("USB", color = scheme.primary, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                    }
                    Button(
                        onClick = { tempIp = "192.168.1.35:8000"; tempToken = "jarvis_local_token" },
                        colors = ButtonDefaults.buttonColors(containerColor = scheme.surfaceVariant),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Wi-Fi", color = scheme.onSurface, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                    }
                    Button(
                        onClick = { tempIp = "192.168.137.1:8000"; tempToken = "jarvis_local_token" },
                        colors = ButtonDefaults.buttonColors(containerColor = scheme.surfaceVariant),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Hotspot", color = ArcGold, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                    }
                }

                Spacer(Modifier.height(14.dp))
                OutlinedTextField(
                    value = tempIp,
                    onValueChange = { tempIp = it },
                    label = { Text("Server Host / IP") },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = scheme.primary,
                        unfocusedBorderColor = scheme.outline.copy(alpha = 0.4f)
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = tempToken,
                    onValueChange = { tempToken = it },
                    label = { Text("Pairing Token") },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = scheme.primary,
                        unfocusedBorderColor = scheme.outline.copy(alpha = 0.4f)
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
                Text("Connect", color = scheme.primary, fontWeight = FontWeight.SemiBold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = scheme.onSurfaceVariant)
            }
        },
        containerColor = scheme.surface
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
