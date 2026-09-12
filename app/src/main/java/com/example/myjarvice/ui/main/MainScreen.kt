package com.example.myjarvice.ui.main

import android.app.Application
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.ui.text.input.PasswordVisualTransformation
import com.example.myjarvice.ui.inbox.RememberInboxScreen
import com.example.myjarvice.ui.inbox.toJarvisPrompt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import android.Manifest
import android.content.pm.PackageManager
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.OutlinedButton
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
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.example.myjarvice.data.ChatSession
import com.example.myjarvice.data.FileTransferManager
import com.example.myjarvice.data.ImageUnderstanding
import com.example.myjarvice.data.PhotoAttachment
import com.example.myjarvice.data.RememberInboxStore
import com.example.myjarvice.theme.ArcGold
import com.example.myjarvice.ui.JarvisArcReactor
import com.example.myjarvice.ui.files.PcExplorerDialog

import com.example.myjarvice.ui.icons.IconActivity
import com.example.myjarvice.ui.icons.IconDocument
import com.example.myjarvice.ui.icons.IconMessage
import com.example.myjarvice.ui.icons.IconPlus
import com.example.myjarvice.ui.icons.IconSettings
import com.example.myjarvice.ui.icons.IconSparkles
import com.example.myjarvice.ui.icons.IconTrash
import com.example.myjarvice.ui.voice.VoiceInfoDialog
import com.example.myjarvice.ui.voice.VoiceModeScreen
import com.example.myjarvice.ui.voice.VoicePickerDialog
import com.example.myjarvice.wake.WakeWordService
import kotlinx.coroutines.launch
import java.io.File
import java.util.Calendar
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun MainScreen(
    onOpenSettings: () -> Unit = {},
    inboxRequest: Long = 0L,
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
    val responseRoute by viewModel.responseRoute.collectAsStateWithLifecycle()

    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val coroutineScope = rememberCoroutineScope()

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        WakeWordService.stop(context.applicationContext)

        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> {
                    WakeWordService.stop(context.applicationContext)
                    viewModel.refreshPreferences()
                }
                Lifecycle.Event.ON_STOP -> {
                    viewModel.exitVoiceMode()
                }
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    var textInput by rememberSaveable { mutableStateOf("") }
    var showIpDialog by remember { mutableStateOf(false) }
    var showVoiceInfo by remember { mutableStateOf(false) }
    var showVoicePicker by remember { mutableStateOf(false) }
    var showToolsMenu by remember { mutableStateOf(false) }
    var showPcExplorer by remember { mutableStateOf(false) }
    var showRememberInbox by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(inboxRequest) { if (inboxRequest > 0) showRememberInbox = true }
    val rememberInbox = remember { RememberInboxStore(context.applicationContext) }
    var pendingChatDeletion by remember { mutableStateOf<String?>(null) }
    var pendingVoiceMode by remember { mutableStateOf(false) }
    val microphonePermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { allowed ->
        if (allowed) {
            if (pendingVoiceMode) viewModel.enterVoiceMode() else viewModel.toggleVoiceInput()
        } else Toast.makeText(context, "Allow microphone access to use voice input.", Toast.LENGTH_LONG).show()
    }
    fun startVoice(conversation: Boolean) {
        if (isThinking) return
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            if (conversation) viewModel.enterVoiceMode() else viewModel.toggleVoiceInput()
        } else {
            pendingVoiceMode = conversation
            microphonePermission.launch(Manifest.permission.RECORD_AUDIO)
        }
    }
    pendingChatDeletion?.let { id ->
        AlertDialog(onDismissRequest = { pendingChatDeletion = null },
            title = { Text(if (id == "all") "Delete all conversations?" else "Delete conversation?") },
            text = { Text("This removes the saved conversation history from this phone.") },
            confirmButton = { TextButton(onClick = {
                if (id == "all") viewModel.clearAllHistory() else viewModel.deleteSession(id)
                pendingChatDeletion = null
            }) { Text("Delete", color = scheme.error) } },
            dismissButton = { TextButton(onClick = { pendingChatDeletion = null }) { Text("Cancel") } })
    }

    val fileDropLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            Toast.makeText(context, "Sending file to host PC...", Toast.LENGTH_SHORT).show()
            coroutineScope.launch {
                val res = FileTransferManager.uploadFileToPc(
                    context = context,
                    uri = uri,
                    serverIp = serverIp,
                    token = serverToken
                )
                res.onSuccess { msg ->
                    Toast.makeText(context, "✅ $msg", Toast.LENGTH_LONG).show()
                }.onFailure { err ->
                    Toast.makeText(context, "Upload failed: ${err.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    // File Attachment State
    var attachedFileName by remember { mutableStateOf<String?>(null) }
    var attachedFileContent by remember { mutableStateOf<String?>(null) }
    var attachedPhoto by remember { mutableStateOf<PhotoAttachment?>(null) }
    var pendingCameraPath by rememberSaveable { mutableStateOf<String?>(null) }
    var attachmentBusy by remember { mutableStateOf(false) }

    fun attachPhoto(uri: Uri) {
        coroutineScope.launch {
            attachmentBusy = true
            val result = withContext(Dispatchers.IO) { ImageUnderstanding.prepare(context, uri) }
            result.onSuccess { photo ->
                attachedPhoto = photo
                attachedFileName = null
                attachedFileContent = null
                val status = if (photo.hasReadableText) "Text read privately on phone" else "Photo attached"
                Toast.makeText(context, status, Toast.LENGTH_SHORT).show()
            }.onFailure { error ->
                Toast.makeText(context, error.message ?: "Could not read that photo.", Toast.LENGTH_SHORT).show()
            }
            attachmentBusy = false
        }
    }

    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { saved ->
        val capturedUri = pendingCameraPath?.let(Uri::parse)
        pendingCameraPath = null
        if (saved && capturedUri != null) attachPhoto(capturedUri)
    }
    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            val imageFile = File(context.cacheDir, "camera/${UUID.randomUUID()}.jpg").apply { parentFile?.mkdirs() }
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", imageFile)
            pendingCameraPath = uri.toString()
            runCatching { cameraLauncher.launch(uri) }.onFailure {
                Toast.makeText(context, "No camera app is available. Choose a photo instead.", Toast.LENGTH_LONG).show()
            }
        } else {
            Toast.makeText(context, "Camera permission is needed to take a photo.", Toast.LENGTH_SHORT).show()
        }
    }
    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? -> uri?.let(::attachPhoto) }

    val filePickerLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) coroutineScope.launch {
            attachmentBusy = true
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val name = context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
                        if (it.moveToFirst()) it.getString(0) else null
                    } ?: "Document"
                    val mime = context.contentResolver.getType(uri).orEmpty()
                    require(mime.startsWith("text/") || name.endsWith(".txt", true) || name.endsWith(".md", true)) {
                        "Choose a text or Markdown file. Add PDFs through Settings → AI & personal knowledge."
                    }
                    val content = context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { reader ->
                        val buffer = CharArray(8000)
                        var count = 0
                        while (count < buffer.size) {
                            val read = reader.read(buffer, count, buffer.size - count)
                            if (read < 0) break
                            count += read
                        }
                        String(buffer, 0, count)
                    } ?: error("The document is unavailable.")
                    name to content
                }
            }
            result.onSuccess { (name, content) ->
                attachedFileName = name; attachedFileContent = content; attachedPhoto = null
            }.onFailure { Toast.makeText(context, it.message ?: "Couldn't attach this document.", Toast.LENGTH_LONG).show() }
            attachmentBusy = false
        }
    }

    if (showPcExplorer) {
        PcExplorerDialog(
            serverIp = serverIp,
            token = serverToken,
            onDismiss = { showPcExplorer = false },
            onAskJarvisAboutFile = { fileName, filePath ->
                viewModel.sendQuery("Jarvis, what is in the file $fileName at $filePath?")
            }
        )
    }

    if (showRememberInbox) {
        RememberInboxScreen(
            store = rememberInbox,
            onDismiss = { showRememberInbox = false },
            onAskJarvis = { item ->
                if (isThinking) Toast.makeText(context, "Wait for the current reply before starting another request.", Toast.LENGTH_SHORT).show()
                else {
                    showRememberInbox = false
                    textInput = item.toJarvisPrompt()
                }
            }
        )
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
                        if (!isThinking) {
                            coroutineScope.launch { drawerState.close() }
                            viewModel.startNewChat()
                            textInput = ""; attachedPhoto = null; attachedFileName = null; attachedFileContent = null
                        } else Toast.makeText(context, "Wait for the current reply first.", Toast.LENGTH_SHORT).show()
                    },
                    onSelectSession = { session ->
                        coroutineScope.launch { drawerState.close() }
                        if (!isThinking) viewModel.loadSession(session)
                        else Toast.makeText(context, "Wait for the current reply first.", Toast.LENGTH_SHORT).show()
                    },
                    onDeleteSession = { sessionId ->
                        if (!isThinking) pendingChatDeletion = sessionId
                    },
                    onClearAllHistory = {
                        if (!isThinking) pendingChatDeletion = "all"
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
                contentWindowInsets = WindowInsets(0, 0, 0, 0),
                topBar = {
                    ChatTopBar(
                        connectionStatus = connectionStatus,
                        onOpenDrawer = { coroutineScope.launch { drawerState.open() } },
                        onStatusClick = { showIpDialog = true },
                        onNewChat = {
                            if (!isThinking) { viewModel.startNewChat(); textInput = ""; attachedPhoto = null; attachedFileName = null; attachedFileContent = null }
                            else Toast.makeText(context, "Wait for this reply before starting a new conversation.", Toast.LENGTH_SHORT).show()
                        },
                        onOpenInbox = { showRememberInbox = true }
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
                    Text(
                        text = responseRoute,
                        style = MaterialTheme.typography.labelSmall,
                        color = scheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp)
                    )
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                    ) {
                        if (chatHistory.isEmpty()) {
                            EmptyChatHero(
                                onPromptSelected = { prompt ->
                                    textInput = prompt
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

                    if (attachmentBusy) {
                        Text("Preparing attachment…", modifier = Modifier.padding(horizontal = 20.dp), style = MaterialTheme.typography.bodySmall)
                        LinearProgressIndicator(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp))
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
                            ChatIconButton("Remove document attachment", onClick = {
                                    attachedFileName = null
                                    attachedFileContent = null
                                }) { Text("×", style = MaterialTheme.typography.titleLarge) }
                        }
                    }

                    if (attachedPhoto != null) {
                        val photo = attachedPhoto!!
                        Row(
                            modifier = Modifier
                                .padding(horizontal = 16.dp, vertical = 4.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(scheme.surfaceVariant)
                                .border(1.dp, scheme.primary.copy(alpha = 0.4f), RoundedCornerShape(10.dp))
                                .padding(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Image(
                                bitmap = photo.bitmap.asImageBitmap(),
                                contentDescription = "Attached photo",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.size(42.dp).clip(RoundedCornerShape(7.dp))
                            )
                            Spacer(Modifier.width(9.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Photo ready", color = scheme.onSurface, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                                Text(
                                    if (photo.hasReadableText) "Text read on this phone" else "Use Strong mode for full visual analysis",
                                    color = scheme.onSurfaceVariant,
                                    fontSize = 11.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            ChatIconButton("Remove photo attachment", onClick = { attachedPhoto = null }) {
                                Text("×", style = MaterialTheme.typography.titleLarge)
                            }
                        }
                    }

                    // Floating Bottom Input Bar
                    ChatComposer(
                        textInput = textInput,
                        onTextChange = { textInput = it.take(4000) },
                        canSendAttachment = attachedFileContent != null || attachedPhoto != null,
                        isListening = isListening,
                        isThinking = isThinking || attachmentBusy,
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
                        onTakePhoto = {
                            showToolsMenu = false
                            if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
                                val imageFile = File(context.cacheDir, "camera/${UUID.randomUUID()}.jpg").apply { parentFile?.mkdirs() }
                                val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", imageFile)
                                pendingCameraPath = uri.toString()
                                runCatching { cameraLauncher.launch(uri) }.onFailure {
                                    Toast.makeText(context, "No camera app is available. Choose a photo instead.", Toast.LENGTH_LONG).show()
                                }
                            } else {
                                cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                            }
                        },
                        onChoosePhoto = {
                            showToolsMenu = false
                            imagePickerLauncher.launch("image/*")
                        },
                        onSendFileToPc = {
                            showToolsMenu = false
                            fileDropLauncher.launch("*/*")
                        },
                        onOpenPcExplorer = {
                            showToolsMenu = false
                            showPcExplorer = true
                        },
                        onSend = {

                            if (textInput.isNotBlank() || attachedFileContent != null || attachedPhoto != null) {
                                val fullQuery = if (attachedFileName != null) {
                                    val question = textInput.ifBlank { "Help me understand this document." }
                                    val header = "$question\n\n[Attached text: ${attachedFileName?.take(80)}]\n"
                                    header + attachedFileContent.orEmpty().take((4000 - header.length).coerceAtLeast(0))
                                } else textInput.ifBlank { "What can you tell me about this photo?" }
                                viewModel.sendQuery(fullQuery.take(4000), attachedPhoto)
                                textInput = ""
                                attachedFileName = null
                                attachedFileContent = null
                                attachedPhoto = null
                            }
                        },
                        onQuickVoice = { startVoice(false) },
                        onVoiceMode = { startVoice(true) }
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
                            modifier = Modifier.size(48.dp).semantics { contentDescription = "Delete conversation ${session.title}" }
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
                    .heightIn(min = 48.dp)
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
            hour < 12 -> "Good morning"
            hour < 18 -> "Good afternoon"
            else -> "Good evening"
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
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
            "What's on your mind?",
            color = scheme.onSurfaceVariant,
            fontSize = 14.sp,
            textAlign = TextAlign.Center
        )

        Spacer(Modifier.height(28.dp))

        // Professional 2x2 Suggestion Cards
        val promptCards = listOf(
            PromptCardItem(
                title = "Talk it through",
                desc = "Make a little space to think",
                prompt = "I have a lot on my mind. Help me figure out where to start.",
                icon = { IconSparkles(tint = ArcGold, size = 18.dp) }
            ),
            PromptCardItem(
                title = "Make it simple",
                desc = "An explanation that makes sense",
                prompt = "Explain how a phone runs an AI model, using a simple example.",
                icon = { IconActivity(tint = Color(0xFF60A5FA), size = 18.dp) }
            ),
            PromptCardItem(
                title = "Quick calculation",
                desc = "Accurate, right on your phone",
                prompt = "Calculate (18 + 7) * 4",
                icon = { IconDocument(tint = scheme.primary, size = 18.dp) }
            ),
            PromptCardItem(
                title = "Your memories",
                desc = "Review what you've saved",
                prompt = "Show memories",
                icon = { IconDocument(tint = Color(0xFF34D399), size = 18.dp) }
            )
        )


        Column(
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            val columns = if (androidx.compose.ui.platform.LocalDensity.current.fontScale > 1.2f) 1 else 2
            promptCards.chunked(columns).forEach { rowItems ->
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

/**
 * Floating Bottom Input Bar (with Host PC & Web Search Tools)
 */

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
                    "For Wi-Fi, enter this PC's reserved local address. USB debugging uses localhost.",
                    color = scheme.onSurfaceVariant,
                    fontSize = 12.sp
                )
                Spacer(Modifier.height(12.dp))

                OutlinedButton(
                    onClick = { tempIp = "127.0.0.1:8000" },
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("Use USB connection")
                }

                Spacer(Modifier.height(14.dp))
                OutlinedTextField(
                    value = tempIp,
                    onValueChange = { tempIp = it },
                    label = { Text("Server Host / IP") },
                    supportingText = { Text("Wi-Fi example: 192.168.0.121:8000") },
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
                    visualTransformation = PasswordVisualTransformation(),
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
