package com.example.myjarvice.ui.settings

import android.os.Build
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import com.example.myjarvice.wake.WakeEvents
import com.example.myjarvice.wake.WakeWordService
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.input.PasswordVisualTransformation
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Computer
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.myjarvice.data.SettingsStore
import com.example.myjarvice.data.SmartMode
import com.example.myjarvice.data.SpeechManager
import com.example.myjarvice.data.BackupPreview
import com.example.myjarvice.data.JarvisBackupManager
import com.example.myjarvice.theme.ThemeMode
import com.example.myjarvice.theme.AssistantStyle
import com.example.myjarvice.ui.icons.IconDocument
import com.example.myjarvice.ui.icons.IconMicrophone
import com.example.myjarvice.ui.icons.IconSparkles
import com.example.myjarvice.ui.icons.IconSpeaker
import com.example.myjarvice.ui.icons.IconVoiceWaveform
import com.example.myjarvice.ui.main.responseModeTitle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

private val settingsDescriptions = mapOf(
    "Appearance" to "Light, dark, AMOLED and wallpaper colors",
    "Voice & speech" to "Voice, pace and spoken replies",
    "Hands-free voice" to "Hey Jarvis, voice match and microphone",
    "Writing style" to "Personal drafts, tone, length, emoji and sign-off",
    "AI & personal knowledge" to "Phone model, memory and documents",
    "PC connection" to "Pair your computer · Wi-Fi and host address",
    "Data & storage" to "Backup, saved items and conversation history",
    "About Jarvis" to "App information"
)

private fun matchesSettingsSearch(title: String, query: String): Boolean =
    query.isBlank() || ("$title ${settingsDescriptions[title].orEmpty()}").contains(query.trim(), ignoreCase = true)

private fun settingsIcon(title: String): ImageVector = when (title) {
    "Appearance" -> Icons.Rounded.Palette
    "Voice & speech" -> Icons.AutoMirrored.Rounded.VolumeUp
    "Hands-free voice" -> Icons.Rounded.Mic
    "Writing style" -> Icons.Rounded.Edit
    "AI & personal knowledge" -> Icons.Rounded.Memory
    "PC connection" -> Icons.Rounded.Computer
    "Data & storage" -> Icons.Rounded.Storage
    else -> Icons.Rounded.Info
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(
    themeMode: ThemeMode,
    dynamicColor: Boolean,
    assistantStyle: AssistantStyle,
    onAssistantStyle: (AssistantStyle) -> Unit,
    onThemeMode: (ThemeMode) -> Unit,
    onDynamicColor: (Boolean) -> Unit,
    wakeEnabled: Boolean,
    onWakeEnabled: (Boolean) -> Unit,
    onOpenVoiceMatch: () -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val settingsStore = remember { SettingsStore(context) }
    val wakeStatus by WakeEvents.status.collectAsState()
    val speechManager = remember { SpeechManager(context) }
    val availableVoices by speechManager.voices.collectAsState()
    DisposableEffect(speechManager) { onDispose { speechManager.shutdown() } }
    var importingModel by remember { mutableStateOf(false) }
    var backupBusy by remember { mutableStateOf(false) }
    var pendingRestore by remember { mutableStateOf<Pair<Uri, BackupPreview>?>(null) }
    var backupRevision by remember { mutableIntStateOf(0) }

    var selectedVoiceId by remember { mutableStateOf(settingsStore.ttsVoice) }
    var speechRate by remember { mutableFloatStateOf(settingsStore.ttsSpeechRate) }
    var pitch by remember { mutableFloatStateOf(settingsStore.ttsPitch) }
    var autoSpeak by remember { mutableStateOf(settingsStore.autoSpeakReplies) }
    var userName by remember { mutableStateOf(settingsStore.userName) }
    var aiPersonality by remember { mutableStateOf(settingsStore.aiPersonality) }
    var temperature by remember { mutableFloatStateOf(settingsStore.temperature) }
    var smartMode by remember { mutableStateOf(settingsStore.smartMode) }
    var onDeviceModelPath by remember { mutableStateOf(settingsStore.onDeviceModelPath) }
    val transferredModelPath = remember {
        File(context.filesDir, "models/jarvis-on-device.litertlm")
            .takeIf { it.isFile }
            ?.absolutePath
            .orEmpty()
    }
    var serverIp by remember { mutableStateOf(settingsStore.serverIp) }
    var serverToken by remember { mutableStateOf(settingsStore.serverToken) }

    var voiceMatchEnabled by remember { mutableStateOf(settingsStore.voiceMatchEnabled) }
    var voiceMatchThreshold by remember { mutableFloatStateOf(settingsStore.voiceMatchThreshold) }
    var isEnrolled by remember { mutableStateOf(settingsStore.isVoiceProfileEnrolled) }
    var assistantPaused by remember { mutableStateOf(settingsStore.assistantPaused) }
    val lastVoiceMatchScore by WakeEvents.lastVoiceMatchScore.collectAsState()
    val lifecycleOwner = LocalLifecycleOwner.current

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                onDeviceModelPath = settingsStore.onDeviceModelPath
                isEnrolled = settingsStore.isVoiceProfileEnrolled
                voiceMatchEnabled = settingsStore.voiceMatchEnabled && isEnrolled
                assistantPaused = settingsStore.assistantPaused
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    var showVoiceDropdown by remember { mutableStateOf(false) }
    var showPersonalityDropdown by remember { mutableStateOf(false) }

    val coroutineScope = rememberCoroutineScope()
    val backupManager = remember { JarvisBackupManager(context) }
    val backupExportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/zip")
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        coroutineScope.launch {
            backupBusy = true
            val result = withContext(Dispatchers.IO) { runCatching { backupManager.exportTo(uri) } }
            result.onSuccess { preview ->
                Toast.makeText(context, "Backup created · ${preview.totalItems} items", Toast.LENGTH_LONG).show()
            }.onFailure { error ->
                Toast.makeText(context, "Backup failed: ${error.message}", Toast.LENGTH_LONG).show()
            }
            backupBusy = false
        }
    }
    val backupImportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        coroutineScope.launch {
            backupBusy = true
            val result = withContext(Dispatchers.IO) { runCatching { backupManager.inspect(uri) } }
            result.onSuccess { pendingRestore = uri to it }
                .onFailure { error -> Toast.makeText(context, "Cannot open backup: ${error.message}", Toast.LENGTH_LONG).show() }
            backupBusy = false
        }
    }
    val modelImportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        coroutineScope.launch {
            importingModel = true
            val library = com.example.myjarvice.data.LocalModelLibrary(context)
            val hadActiveModel = library.activeModel() != null
            library.fallbackModel()
            val outcome = withContext(Dispatchers.IO) {
                runCatching {
                    library.importCandidate(uri).absolutePath
                }.onFailure { if (it is kotlinx.coroutines.CancellationException) throw it }
            }
            outcome.onSuccess { path ->
                if (!hadActiveModel) {
                    onDeviceModelPath = path
                    settingsStore.onDeviceModelPath = path
                    settingsStore.localFallbackModelPath = path
                }
                Toast.makeText(context, if (hadActiveModel) "Candidate added. Current model kept; compare it in Settings." else "On-device model imported", Toast.LENGTH_LONG).show()
            }.onFailure { error ->
                Toast.makeText(context, "Model import failed: ${error.message}", Toast.LENGTH_LONG).show()
            }
            importingModel = false
        }
    }

    val scheme = MaterialTheme.colorScheme

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(scheme.background)
            .safeDrawingPadding()
            .imePadding()
            .padding(horizontal = 20.dp, vertical = 16.dp)
    ) {
        com.example.myjarvice.ui.JarvisPageHeader("Settings", "Make Jarvis work your way", onBack)
        var sectionQuery by rememberSaveable { mutableStateOf("") }
        OutlinedTextField(value = sectionQuery, onValueChange = { sectionQuery = it },
            placeholder = { Text("Find a setting") }, singleLine = true,
            modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp),
            leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
            trailingIcon = { if (sectionQuery.isNotEmpty()) IconButton(onClick = { sectionQuery = "" }) {
                Icon(Icons.Rounded.Close, contentDescription = "Clear settings search")
            } })
        Spacer(Modifier.height(16.dp))

        // ==========================================
        // 1. APPEARANCE & THEME
        // ==========================================
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            SettingsCard {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(Modifier.weight(1f).padding(end = 12.dp)) {
                        Text(
                            if (assistantPaused) "Jarvis is paused" else "Jarvis is active",
                            color = scheme.onSurface,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            if (assistantPaused)
                                "Wake listening and new requests are blocked on this phone."
                            else
                                "Pause instantly to stop listening and block new requests.",
                            color = scheme.onSurfaceVariant,
                            fontSize = 12.sp
                        )
                    }
                    Button(
                        onClick = {
                            assistantPaused = !assistantPaused
                            settingsStore.assistantPaused = assistantPaused
                            if (assistantPaused) {
                                WakeWordService.stop(context)
                                WakeEvents.status.value = "Jarvis is paused"
                                Toast.makeText(context, "Jarvis paused on this phone", Toast.LENGTH_SHORT).show()
                            } else {
                                if (wakeEnabled) WakeWordService.start(context)
                                Toast.makeText(context, "Jarvis resumed", Toast.LENGTH_SHORT).show()
                            }
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (assistantPaused) scheme.primary else scheme.error,
                            contentColor = if (assistantPaused) scheme.onPrimary else scheme.onError
                        )
                    ) { Text(if (assistantPaused) "Resume" else "Pause") }
                }
            }
            Spacer(Modifier.height(12.dp))
            if (settingsDescriptions.keys.none { matchesSettingsSearch(it, sectionQuery) }) {
                Text("No settings found", style = MaterialTheme.typography.titleMedium)
                Text("Try voice, model, appearance, or PC.", style = MaterialTheme.typography.bodyMedium,
                    color = scheme.onSurfaceVariant, modifier = Modifier.padding(top = 8.dp))
                TextButton(onClick = { sectionQuery = "" }) { Text("Show all settings") }
            }
            SettingsSection("Appearance", initiallyExpanded = false, query = sectionQuery) {


                SettingsCard {
                    Text("Assistant style", style = MaterialTheme.typography.titleSmall)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ThemeChip("Pixel", assistantStyle == AssistantStyle.PIXEL) { onAssistantStyle(AssistantStyle.PIXEL) }
                        ThemeChip("Jarvis", assistantStyle == AssistantStyle.JARVIS) { onAssistantStyle(AssistantStyle.JARVIS) }
                    }
                    Text("Pixel uses calm Material surfaces. Jarvis adds cyan and amber accents. Both are still Jarvis.",
                        style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
                    HorizontalDivider(Modifier.padding(vertical = 14.dp))
                    Text("Theme Mode", color = scheme.onSurface, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                    Spacer(Modifier.height(10.dp))

                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        ThemeChip("System", themeMode == ThemeMode.SYSTEM) { onThemeMode(ThemeMode.SYSTEM) }
                        ThemeChip("Dark", themeMode == ThemeMode.DARK) { onThemeMode(ThemeMode.DARK) }
                        ThemeChip("AMOLED", themeMode == ThemeMode.AMOLED) { onThemeMode(ThemeMode.AMOLED) }
                        ThemeChip("Light", themeMode == ThemeMode.LIGHT) { onThemeMode(ThemeMode.LIGHT) }
                    }

                    val dynamicSupported = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
                    if (dynamicSupported) {
                        Spacer(Modifier.height(14.dp))
                        HorizontalDivider(color = scheme.outline.copy(alpha = 0.2f))
                        Spacer(Modifier.height(12.dp))

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(Modifier.weight(1f).padding(end = 12.dp)) {
                                Text("Wallpaper colors", color = scheme.onSurface, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                                Text("Match your phone's color palette", color = scheme.onSurfaceVariant, fontSize = 12.sp)
                            }
                            Switch(
                                checked = dynamicColor,
                                onCheckedChange = onDynamicColor,
                                colors = SwitchDefaults.colors(checkedThumbColor = scheme.onPrimary, checkedTrackColor = scheme.primary)
                            )
                        }
                    }
                }

                Spacer(Modifier.height(24.dp))


            }
            SettingsSection("Voice & speech", initiallyExpanded = false, query = sectionQuery) {


                SettingsCard {
                    // TTS Voice Selector
                    Text("Voice Model", color = scheme.onSurface, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                    Spacer(Modifier.height(8.dp))

                    Box(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(scheme.surfaceVariant)
                                .border(1.dp, scheme.outline.copy(alpha = 0.3f), RoundedCornerShape(10.dp))
                                .clickable { showVoiceDropdown = true }
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                IconSpeaker(tint = scheme.primary, size = 16.dp)
                                Spacer(Modifier.width(10.dp))
                                Text(
                                    if (selectedVoiceId.isBlank()) "Default Engine Voice" else selectedVoiceId.take(28),
                                    color = scheme.onSurface,
                                    fontSize = 13.sp
                                )
                            }
                            Text("▾", color = scheme.onSurfaceVariant, fontSize = 12.sp)
                        }

                        DropdownMenu(
                            expanded = showVoiceDropdown,
                            onDismissRequest = { showVoiceDropdown = false },
                            modifier = Modifier.background(scheme.surface)
                        ) {
                            DropdownMenuItem(
                                text = { Text("Default System Voice", color = scheme.onSurface) },
                                onClick = {
                                    selectedVoiceId = ""
                                    speechManager.applyVoice("")
                                    showVoiceDropdown = false
                                }
                            )
                            availableVoices.forEach { v ->
                                DropdownMenuItem(
                                    text = { Text(v.label, color = scheme.onSurface) },
                                    onClick = {
                                        selectedVoiceId = v.id
                                        speechManager.applyVoice(v.id)
                                        showVoiceDropdown = false
                                    }
                                )
                            }
                        }
                    }

                    Spacer(Modifier.height(10.dp))

                    // Test Voice Button
                    Button(
                        onClick = {
                            speechManager.previewVoice(selectedVoiceId, "Hi, I’m Jarvis. This is how my voice sounds.")
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = scheme.surfaceVariant),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        IconVoiceWaveform(tint = scheme.primary, size = 14.dp)
                        Spacer(Modifier.width(8.dp))
                        Text("Test Voice Audio", color = scheme.primary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    }

                    Spacer(Modifier.height(14.dp))
                    HorizontalDivider(color = scheme.outline.copy(alpha = 0.2f))
                    Spacer(Modifier.height(12.dp))

                    // Speech Rate Slider
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Speech Rate", color = scheme.onSurface, fontSize = 13.sp)
                        Text("${String.format("%.2f", speechRate)}x", color = scheme.primary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    }
                    Slider(
                        value = speechRate,
                        onValueChange = {
                            speechRate = it
                            speechManager.applySpeechRate(it)
                        },
                        valueRange = 0.6f..1.6f,
                        steps = 10,
                        colors = SliderDefaults.colors(thumbColor = scheme.primary, activeTrackColor = scheme.primary)
                    )

                    // Pitch Slider
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Voice Pitch", color = scheme.onSurface, fontSize = 13.sp)
                        Text("${String.format("%.2f", pitch)}x", color = scheme.primary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    }
                    Slider(
                        value = pitch,
                        onValueChange = {
                            pitch = it
                            speechManager.applyPitch(it)
                        },
                        valueRange = 0.7f..1.3f,
                        steps = 6,
                        colors = SliderDefaults.colors(thumbColor = scheme.primary, activeTrackColor = scheme.primary)
                    )

                    Spacer(Modifier.height(10.dp))
                    HorizontalDivider(color = scheme.outline.copy(alpha = 0.2f))
                    Spacer(Modifier.height(10.dp))

                    // Auto-speak responses
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(Modifier.weight(1f).padding(end = 12.dp)) {
                            Text("Auto-Speak Responses", color = scheme.onSurface, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                            Text("Read replies aloud using TTS", color = scheme.onSurfaceVariant, fontSize = 12.sp)
                        }
                        Switch(
                            checked = autoSpeak,
                            onCheckedChange = {
                                autoSpeak = it
                                settingsStore.autoSpeakReplies = it
                            },
                            colors = SwitchDefaults.colors(checkedThumbColor = scheme.onPrimary, checkedTrackColor = scheme.primary)
                        )
                    }
                }

                Spacer(Modifier.height(24.dp))


            }
            SettingsSection("Hands-free voice", initiallyExpanded = false, query = sectionQuery) {


                SettingsCard {
                    // Wake Word Toggle
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(Modifier.weight(1f).padding(end = 12.dp)) {
                            Text("Listen for Hey Jarvis", color = scheme.onSurface, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                            Text(
                                when {
                                    assistantPaused -> "Paused • resume Jarvis to listen"
                                    wakeEnabled -> wakeStatus
                                    else -> "Off • enable to set up"
                                },
                                color = scheme.onSurfaceVariant,
                                fontSize = 12.sp
                            )
                        }
                        Switch(
                            checked = wakeEnabled,
                            enabled = !assistantPaused,
                            onCheckedChange = onWakeEnabled,
                            colors = SwitchDefaults.colors(checkedThumbColor = scheme.onPrimary, checkedTrackColor = scheme.primary)
                        )
                    }

                    Spacer(Modifier.height(14.dp))
                    Text(
                        if (voiceMatchEnabled && isEnrolled)
                            "Wake detection and voice matching stay on this phone. Jarvis opens hands-free only when the enrolled voice matches."
                        else
                            "Set up and enable your voice profile first. Automatic wake will not open Jarvis without it. First setup downloads a 40 MB English recognition model.",
                        color = scheme.onSurfaceVariant,
                        fontSize = 12.sp
                    )
                    Text("Say only ‘Hey Jarvis’, pause for the popup and ready tone, then speak. Hi/Okay Jarvis and sentences mentioning Jarvis do not count. Your phone must be unlocked.", color = scheme.onSurfaceVariant, fontSize = 12.sp)
                    Button(onClick = {
                        runCatching { context.startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${context.packageName}"))) }
                            .onFailure { Toast.makeText(context, "Open Android Settings → Apps → Jarvis → Display over other apps", Toast.LENGTH_LONG).show() }
                    }) { Text("Allow Jarvis popup over other apps") }
                    TextButton(onClick = {
                        context.startActivity(Intent(context, com.example.myjarvice.AssistantPopupActivity::class.java))
                    }) { Text("Preview Jarvis popup") }
                    HorizontalDivider(color = scheme.outline.copy(alpha = 0.2f))
                    Spacer(Modifier.height(12.dp))

                    // Personal voice profile
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(Modifier.weight(1f).padding(end = 12.dp)) {
                            Text("Personal voice profile", color = scheme.onSurface, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                            Text(
                                when {
                                    !isEnrolled -> "Set up three voice samples"
                                    voiceMatchEnabled -> "Used for hands-free wake protection"
                                    else -> "Saved on this phone · protection is off"
                                },
                                color = if (isEnrolled) scheme.primary else scheme.onSurfaceVariant,
                                fontSize = 12.sp
                            )
                        }
                        Switch(
                            checked = voiceMatchEnabled,
                            enabled = isEnrolled && !assistantPaused,
                            onCheckedChange = {
                                voiceMatchEnabled = it
                                settingsStore.voiceMatchEnabled = it
                                if (!it) onWakeEnabled(false)
                            },
                            colors = SwitchDefaults.colors(checkedThumbColor = scheme.onPrimary, checkedTrackColor = scheme.primary)
                        )
                    }

                    Spacer(Modifier.height(12.dp))

                    // Calibration Button
                    Button(
                        onClick = onOpenVoiceMatch,
                        colors = ButtonDefaults.buttonColors(containerColor = scheme.surfaceVariant),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, scheme.primary.copy(alpha = 0.4f), RoundedCornerShape(10.dp))
                    ) {
                        IconMicrophone(tint = scheme.primary, size = 16.dp)
                        Spacer(Modifier.width(8.dp))
                        Text(
                            if (isEnrolled) "Retrain voice profile" else "Set up voice profile",
                            color = scheme.primary,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 13.sp
                        )
                    }

                    if (isEnrolled) {
                        Spacer(Modifier.height(10.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Match Sensitivity", color = scheme.onSurface, fontSize = 13.sp)
                            Text(
                                when {
                                    voiceMatchThreshold >= 0.86f -> "Very strict"
                                    else -> "Strict"
                                },
                                color = scheme.primary,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                        Slider(
                            value = voiceMatchThreshold,
                            onValueChange = {
                                voiceMatchThreshold = it
                                settingsStore.voiceMatchThreshold = it
                            },
                            valueRange = 0.78f..0.90f,
                            steps = 5,
                            colors = SliderDefaults.colors(thumbColor = scheme.primary, activeTrackColor = scheme.primary)
                        )
                        Text(
                            lastVoiceMatchScore?.let { "Last wake match: ${(it * 100).toInt()}%" }
                                ?: "No wake attempts checked yet",
                            color = scheme.onSurfaceVariant,
                            fontSize = 12.sp
                        )
                        Text(
                            "Voice matching improves privacy, but it can make mistakes and is not a replacement for your phone lock or Android biometrics.",
                            color = scheme.onSurfaceVariant,
                            fontSize = 12.sp
                        )
                        TextButton(onClick = {
                            settingsStore.clearVoiceProfile()
                            onWakeEnabled(false)
                            voiceMatchEnabled = false
                            isEnrolled = false
                            WakeEvents.lastVoiceMatchScore.value = null
                        }) {
                            Text("Delete voice profile", color = scheme.error)
                        }
                    }
                }

                Spacer(Modifier.height(24.dp))


            }
            SettingsSection("Writing style", initiallyExpanded = false, query = sectionQuery) {
                key(backupRevision) { WritingProfilePanel() }
            }
            SettingsSection("AI & personal knowledge", initiallyExpanded = false, query = sectionQuery) {


                LocalKnowledgePanel()
                Spacer(Modifier.height(16.dp))

                SettingsCard {
                    OutlinedTextField(
                        value = userName,
                        onValueChange = {
                            userName = it
                            settingsStore.userName = it
                        },
                        label = { Text("Your name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = scheme.primary,
                            unfocusedBorderColor = scheme.outline.copy(alpha = 0.4f)
                        )
                    )

                    Spacer(Modifier.height(12.dp))

                    // AI Personality
                    Text("Conversation style", color = scheme.onSurface, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                    Spacer(Modifier.height(6.dp))

                    Box(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(scheme.surfaceVariant)
                                .border(1.dp, scheme.outline.copy(alpha = 0.3f), RoundedCornerShape(10.dp))
                                .clickable { showPersonalityDropdown = true }
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(when {
                                aiPersonality.contains("Technical", true) -> "Thoughtful & detailed"
                                aiPersonality.contains("Concise", true) -> "Brief & direct"
                                aiPersonality.contains("Iron Man", true) -> "Natural & warm"
                                else -> aiPersonality
                            }, color = scheme.onSurface, fontSize = 13.sp)
                            Text("▾", color = scheme.onSurfaceVariant, fontSize = 12.sp)
                        }

                        DropdownMenu(
                            expanded = showPersonalityDropdown,
                            onDismissRequest = { showPersonalityDropdown = false },
                            modifier = Modifier.background(scheme.surface)
                        ) {
                            listOf(
                                "Natural & warm",
                                "Brief & direct",
                                "Thoughtful & detailed"
                            ).forEach { p ->
                                DropdownMenuItem(
                                    text = { Text(p, color = scheme.onSurface) },
                                    onClick = {
                                        aiPersonality = p
                                        settingsStore.aiPersonality = p
                                        showPersonalityDropdown = false
                                    }
                                )
                            }
                        }
                    }

                    Spacer(Modifier.height(12.dp))

                    Text("PC model", style = MaterialTheme.typography.titleSmall)
                    Text("Configured on your PC server. Changing the phone settings does not replace the server model.",
                        style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)

                    Spacer(Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Temperature (Creativity)", color = scheme.onSurface, fontSize = 13.sp)
                        Text(String.format("%.1f", temperature), color = scheme.primary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    }
                    Slider(
                        value = temperature,
                        onValueChange = {
                            temperature = it
                            settingsStore.temperature = it
                        },
                        valueRange = 0.0f..1.0f,
                        steps = 10,
                        colors = SliderDefaults.colors(thumbColor = scheme.primary, activeTrackColor = scheme.primary)
                    )

                    Spacer(Modifier.height(16.dp))
                    HorizontalDivider(color = scheme.outline.copy(alpha = 0.2f))
                    Spacer(Modifier.height(12.dp))

                    Text("Response model", color = scheme.onSurface, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        when (smartMode) {
                            SmartMode.FAST_ON_DEVICE -> "Private text replies from the model stored on this phone."
                            SmartMode.STRONG_HOST -> "Use the model, web search, and tools on your paired computer."
                            SmartMode.AUTO -> "Use your PC when available, with the phone model as a private fallback."
                        },
                        color = scheme.onSurfaceVariant,
                        fontSize = 12.sp
                    )
                    Spacer(Modifier.height(8.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        listOf(
                            SmartMode.AUTO,
                            SmartMode.FAST_ON_DEVICE,
                            SmartMode.STRONG_HOST
                        ).forEach { mode ->
                            Button(
                                onClick = {
                                    smartMode = mode
                                    settingsStore.smartMode = mode
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (smartMode == mode) scheme.primary else scheme.surfaceVariant,
                                    contentColor = if (smartMode == mode) scheme.onPrimary else scheme.primary
                                ),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier
                            ) { Text(responseModeTitle(mode), fontSize = 12.sp, fontWeight = FontWeight.SemiBold) }
                        }
                    }

                    Spacer(Modifier.height(10.dp))
                    Button(
                        // Some Android document providers classify .litertlm as an unknown
                        // type. Request every type so the imported model remains visible.
                        onClick = { modelImportLauncher.launch(arrayOf("*/*")) },
                        enabled = !importingModel,
                        colors = ButtonDefaults.buttonColors(containerColor = scheme.surfaceVariant),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, scheme.primary.copy(alpha = 0.4f), RoundedCornerShape(10.dp))
                    ) {
                        IconDocument(tint = scheme.primary, size = 16.dp)
                        Spacer(Modifier.width(8.dp))
                        Text(
                            if (onDeviceModelPath.isBlank() && transferredModelPath.isBlank()) "Import LiteRT-LM Model" else "Add model · keep current model",
                            color = scheme.primary,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 13.sp
                        )
                    }
                    if (importingModel) {
                        LinearProgressIndicator(Modifier.fillMaxWidth())
                        Text("Importing model… Keep Jarvis open.", style = MaterialTheme.typography.bodySmall)
                    }
                    TextButton(onClick = {
                        context.startActivity(Intent(context, com.example.myjarvice.LocalModelBenchmarkActivity::class.java))
                    }, enabled = !importingModel, modifier = Modifier.fillMaxWidth()) {
                        Text("Compare local models")
                    }
                    val displayedModelPath = onDeviceModelPath.ifBlank { transferredModelPath }
                    if (displayedModelPath.isNotBlank()) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "Installed: ${File(displayedModelPath).name}",
                            color = scheme.primary,
                            fontSize = 12.sp
                        )
                    }
                }

                Spacer(Modifier.height(24.dp))


            }
            SettingsSection("PC connection", initiallyExpanded = false, query = sectionQuery) {


                SettingsCard {
                    OutlinedTextField(
                        value = serverIp,
                        onValueChange = {
                            serverIp = it
                            settingsStore.serverIp = it
                        },
                        label = { Text("PC address") },
                        supportingText = { Text("For example, 192.168.1.10:8000") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = scheme.primary,
                            unfocusedBorderColor = scheme.outline.copy(alpha = 0.4f)
                        )
                    )

                    Spacer(Modifier.height(10.dp))

                    OutlinedTextField(
                        value = serverToken,
                        onValueChange = {
                            serverToken = it
                            settingsStore.serverToken = it
                        },
                        label = { Text("Pairing Token") },
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = scheme.primary,
                            unfocusedBorderColor = scheme.outline.copy(alpha = 0.4f)
                        )
                    )
                }

                Spacer(Modifier.height(24.dp))


            }
            SettingsSection("Data & storage", initiallyExpanded = false, query = sectionQuery) {

                    SettingsCard {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconDocument(tint = scheme.primary, size = 20.dp)
                            Spacer(Modifier.width(10.dp))
                            Column {
                                Text("Backup & restore", style = MaterialTheme.typography.titleSmall)
                                Text("Keep your personal Jarvis data under your control",
                                    style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
                            }
                        }
                        Spacer(Modifier.height(12.dp))
                        Text("Back up conversations, memories, imported text, saved items and preferences to a file you choose.",
                            style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant)
                        Spacer(Modifier.height(8.dp))
                        Text("Never included: PC address or token, voice profile, wake state, and AI model files.",
                            style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
                        Text("Backup files are not encrypted. Keep them somewhere private.",
                            style = MaterialTheme.typography.bodySmall, color = scheme.error,
                            modifier = Modifier.padding(top = 6.dp))
                        if (backupBusy) {
                            Spacer(Modifier.height(14.dp))
                            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                            Text("Preparing your data…", style = MaterialTheme.typography.labelMedium,
                                color = scheme.onSurfaceVariant, modifier = Modifier.padding(top = 6.dp))
                        }
                        Spacer(Modifier.height(14.dp))
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Button(
                                onClick = {
                                    val date = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(java.util.Date())
                                    backupExportLauncher.launch("jarvis-backup-$date.jarvisbackup")
                                },
                                enabled = !backupBusy,
                                modifier = Modifier.weight(1f).heightIn(min = 48.dp)
                            ) { Text("Create backup") }
                            androidx.compose.material3.OutlinedButton(
                                onClick = { backupImportLauncher.launch(arrayOf("application/zip", "application/octet-stream", "*/*")) },
                                enabled = !backupBusy,
                                modifier = Modifier.weight(1f).heightIn(min = 48.dp)
                            ) { Text("Restore") }
                        }
                    }
                    Spacer(Modifier.height(16.dp))

            }
            SettingsSection("About Jarvis", initiallyExpanded = false, query = sectionQuery) {


                SettingsCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconSparkles(tint = scheme.primary, size = 20.dp)
                        Spacer(Modifier.width(10.dp))
                        Column {
                            Text("JARVIS 1.0", color = scheme.onSurface, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                            Text("Your personal assistant, on phone and PC", color = scheme.onSurfaceVariant, fontSize = 12.sp)
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Chat, save ideas, and find what you need. AI capabilities depend on your installed phone model and configured PC.",
                        color = scheme.onSurfaceVariant,
                        fontSize = 13.sp,
                        lineHeight = 16.sp
                    )
                }


            }
        }
        Spacer(Modifier.height(8.dp))
    }

    pendingRestore?.let { (uri, preview) ->
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { if (!backupBusy) pendingRestore = null },
            title = { Text("Merge this backup?") },
            text = {
                Column {
                    Text("Created ${java.text.DateFormat.getDateTimeInstance(java.text.DateFormat.MEDIUM, java.text.DateFormat.SHORT).format(java.util.Date(preview.createdAt))}",
                        style = MaterialTheme.typography.labelMedium, color = scheme.onSurfaceVariant)
                    Spacer(Modifier.height(8.dp))
                    Text("Jarvis found ${preview.totalItems} items:")
                    Text("${preview.conversations} conversations · ${preview.memories} memories · ${preview.documents} documents")
                    Text("${preview.tasks} tasks · ${preview.savedItems} saved items · ${preview.mediaFiles} media files")
                    Spacer(Modifier.height(12.dp))
                    Text("Restore adds missing items and updates older matching conversations. It does not delete newer data.",
                        color = scheme.onSurfaceVariant)
                    Text("Connection credentials, voiceprints and model files are never restored.",
                        color = scheme.onSurfaceVariant, modifier = Modifier.padding(top = 8.dp))
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    coroutineScope.launch {
                        backupBusy = true
                        val result = withContext(Dispatchers.IO) { runCatching { backupManager.restoreFrom(uri) } }
                        result.onSuccess {
                            selectedVoiceId = settingsStore.ttsVoice
                            speechRate = settingsStore.ttsSpeechRate
                            pitch = settingsStore.ttsPitch
                            autoSpeak = settingsStore.autoSpeakReplies
                            userName = settingsStore.userName
                            aiPersonality = settingsStore.aiPersonality
                            temperature = settingsStore.temperature
                            smartMode = settingsStore.smartMode
                            onThemeMode(settingsStore.themeMode)
                            onDynamicColor(settingsStore.dynamicColor)
                            onAssistantStyle(settingsStore.assistantStyle)
                            backupRevision++
                            Toast.makeText(context, "Backup merged successfully", Toast.LENGTH_LONG).show()
                        }.onFailure { error ->
                            Toast.makeText(context, "Restore failed: ${error.message}", Toast.LENGTH_LONG).show()
                        }
                        backupBusy = false
                        pendingRestore = null
                    }
                }, enabled = !backupBusy) { Text("Merge backup") }
            },
            dismissButton = { TextButton(onClick = { pendingRestore = null }, enabled = !backupBusy) { Text("Cancel") } }
        )
    }
}

@Composable
private fun SettingsSection(title: String, initiallyExpanded: Boolean = false, query: String = "", content: @Composable () -> Unit) {
    var expanded by rememberSaveable { mutableStateOf(initiallyExpanded) }
    val subtitle = settingsDescriptions[title].orEmpty()
    if (!matchesSettingsSearch(title, query)) return
    androidx.compose.material3.Surface(
        onClick = { expanded = !expanded },
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        shape = RoundedCornerShape(20.dp),
        color = if (expanded) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerLow,
        tonalElevation = if (expanded) 2.dp else 0.dp
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            com.example.myjarvice.ui.JarvisIconBadge(
                settingsIcon(title),
                modifier = Modifier.size(40.dp),
                containerColor = if (expanded) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHighest,
                contentColor = if (expanded) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.primary
            )
            Column(Modifier.weight(1f).padding(end = 12.dp)) {
                Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(start = 14.dp))
                Text(subtitle, style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 14.dp, top = 4.dp))
            }
            Icon(if (expanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                contentDescription = if (expanded) "Collapse $title" else "Expand $title",
                tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
    if (expanded) content()
}

@Composable
private fun SettingsCard(content: @Composable () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    androidx.compose.material3.Surface(
        modifier = Modifier
            .fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = scheme.surfaceContainerLow,
        border = androidx.compose.foundation.BorderStroke(1.dp, scheme.outlineVariant.copy(alpha = 0.65f)),
        tonalElevation = 1.dp
    ) {
        Column(Modifier.padding(18.dp)) { content() }
    }
}

@Composable
private fun ThemeChip(label: String, selected: Boolean, onClick: () -> Unit) {
    androidx.compose.material3.FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label) },
        modifier = Modifier.padding(vertical = 4.dp)
    )
}
