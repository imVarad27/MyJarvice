package com.example.myjarvice.ui.settings

import android.os.Build
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.myjarvice.data.SettingsStore
import com.example.myjarvice.data.SmartMode
import com.example.myjarvice.data.SpeechManager
import com.example.myjarvice.theme.ThemeMode
import com.example.myjarvice.ui.icons.IconDocument
import com.example.myjarvice.ui.icons.IconMicrophone
import com.example.myjarvice.ui.icons.IconSparkles
import com.example.myjarvice.ui.icons.IconSpeaker
import com.example.myjarvice.ui.icons.IconVoiceWaveform
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(
    themeMode: ThemeMode,
    dynamicColor: Boolean,
    onThemeMode: (ThemeMode) -> Unit,
    onDynamicColor: (Boolean) -> Unit,
    wakeEnabled: Boolean,
    onWakeEnabled: (Boolean) -> Unit,
    onOpenVoiceMatch: () -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val settingsStore = remember { SettingsStore(context) }
    val speechManager = remember { SpeechManager(context) }
    val availableVoices by speechManager.voices.collectAsState()
    DisposableEffect(speechManager) { onDispose { speechManager.shutdown() } }
    var importingModel by remember { mutableStateOf(false) }

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

    var showVoiceDropdown by remember { mutableStateOf(false) }
    var showPersonalityDropdown by remember { mutableStateOf(false) }

    val coroutineScope = rememberCoroutineScope()
    val modelImportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        coroutineScope.launch {
            importingModel = true
            val outcome = withContext(Dispatchers.IO) {
                runCatching {
                    val modelDir = File(context.filesDir, "models").apply { mkdirs() }
                    val destination = File(modelDir, "jarvis-on-device.litertlm")
                    val atomic = android.util.AtomicFile(destination)
                    val output = atomic.startWrite()
                    try {
                        context.contentResolver.openInputStream(uri)?.use { input ->
                            check(input.copyTo(output) > 0) { "The selected model is empty" }
                        } ?: error("Unable to open the selected model")
                        atomic.finishWrite(output)
                    } catch (error: Exception) {
                        atomic.failWrite(output)
                        throw error
                    }
                    destination.absolutePath
                }
            }
            outcome.onSuccess { path ->
                onDeviceModelPath = path
                settingsStore.onDeviceModelPath = path
                Toast.makeText(context, "On-device model imported", Toast.LENGTH_SHORT).show()
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
        // Top Bar
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .semantics { contentDescription = "Back from settings" }
                    .clip(CircleShape)
                    .background(scheme.surface)
                    .border(1.dp, scheme.outline.copy(alpha = 0.3f), CircleShape)
                    .clickable { onBack() },
                contentAlignment = Alignment.Center
            ) {
                Text("←", color = scheme.onBackground, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.width(14.dp))
            Column {
                Text(
                    "Settings",
                    color = scheme.onBackground,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    "Make Jarvis work your way",
                    color = scheme.onSurfaceVariant,
                    fontSize = 12.sp
                )
            }
        }

        Spacer(Modifier.height(24.dp))

        // ==========================================
        // 1. APPEARANCE & THEME
        // ==========================================
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            SettingsSection("Appearance", initiallyExpanded = true) {


                SettingsCard {
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
            SettingsSection("Voice & speech", initiallyExpanded = false) {


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
            SettingsSection("Hands-free voice", initiallyExpanded = false) {


                SettingsCard {
                    // Wake Word Toggle
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(Modifier.weight(1f).padding(end = 12.dp)) {
                            Text("Listen for Hey Jarvis", color = scheme.onSurface, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                            Text("Hands-free background detection", color = scheme.onSurfaceVariant, fontSize = 12.sp)
                        }
                        Switch(
                            checked = wakeEnabled,
                            onCheckedChange = onWakeEnabled,
                            colors = SwitchDefaults.colors(checkedThumbColor = scheme.onPrimary, checkedTrackColor = scheme.primary)
                        )
                    }

                    Spacer(Modifier.height(14.dp))
                    HorizontalDivider(color = scheme.outline.copy(alpha = 0.2f))
                    Spacer(Modifier.height(12.dp))

                    // Voice Match Biometric
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(Modifier.weight(1f).padding(end = 12.dp)) {
                            Text("Voice Match Verification", color = scheme.onSurface, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                            Text(
                                if (isEnrolled) "Profile Enrolled (Active)" else "Profile Not Calibrated",
                                color = if (isEnrolled) scheme.primary else scheme.onSurfaceVariant,
                                fontSize = 12.sp
                            )
                        }
                        Switch(
                            checked = voiceMatchEnabled && isEnrolled,
                            enabled = isEnrolled,
                            onCheckedChange = {
                                voiceMatchEnabled = it
                                settingsStore.voiceMatchEnabled = it
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
                            if (isEnrolled) "Retrain Voice Profile" else "Calibrate Voice Profile (3 Steps)",
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
                                    voiceMatchThreshold >= 0.78f -> "Strict"
                                    voiceMatchThreshold >= 0.70f -> "Standard"
                                    else -> "Lenient"
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
                            valueRange = 0.60f..0.85f,
                            steps = 5,
                            colors = SliderDefaults.colors(thumbColor = scheme.primary, activeTrackColor = scheme.primary)
                        )
                    }
                }

                Spacer(Modifier.height(24.dp))


            }
            SettingsSection("AI & personal knowledge", initiallyExpanded = false) {


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

                    Text("Smart Mode", color = scheme.onSurface, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        when (smartMode) {
                            SmartMode.FAST_ON_DEVICE -> "Fast & private: uses the model stored on this phone"
                            SmartMode.STRONG_HOST -> "Strong: always uses your connected PC/server model"
                            SmartMode.AUTO -> "Automatic: uses PC when connected, otherwise your phone model"
                        },
                        color = scheme.onSurfaceVariant,
                        fontSize = 12.sp
                    )
                    Spacer(Modifier.height(8.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        listOf(
                            SmartMode.FAST_ON_DEVICE to "Fast",
                            SmartMode.STRONG_HOST to "Strong",
                            SmartMode.AUTO to "Auto"
                        ).forEach { (mode, label) ->
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
                            ) { Text(label, fontSize = 12.sp, fontWeight = FontWeight.SemiBold) }
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
                            if (onDeviceModelPath.isBlank() && transferredModelPath.isBlank()) "Import LiteRT-LM Model" else "Replace On-device Model",
                            color = scheme.primary,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 13.sp
                        )
                    }
                    if (importingModel) {
                        LinearProgressIndicator(Modifier.fillMaxWidth())
                        Text("Importing model… Keep Jarvis open.", style = MaterialTheme.typography.bodySmall)
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
            SettingsSection("PC connection", initiallyExpanded = false) {


                SettingsCard {
                    OutlinedTextField(
                        value = serverIp,
                        onValueChange = {
                            serverIp = it
                            settingsStore.serverIp = it
                        },
                        label = { Text("Host Server IP:Port") },
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
            SettingsSection("Data & storage", initiallyExpanded = false) {

                    SettingsCard {
                        Text("Conversation history", style = MaterialTheme.typography.titleSmall)
                        Spacer(Modifier.height(8.dp))
                        Text("Open the conversation sidebar to review or delete your chats. Saved inbox items have their own delete controls.",
                            style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant)
                    }
                    Spacer(Modifier.height(16.dp))

            }
            SettingsSection("About Jarvis", initiallyExpanded = false) {


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
                        fontSize = 11.sp,
                        lineHeight = 16.sp
                    )
                }


            }
        }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun SettingsSection(title: String, initiallyExpanded: Boolean = false, content: @Composable () -> Unit) {
    var expanded by rememberSaveable { mutableStateOf(initiallyExpanded) }
    androidx.compose.material3.Surface(
        onClick = { expanded = !expanded },
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            Text(if (expanded) "−" else "+", style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.semantics { contentDescription = if (expanded) "Collapse $title" else "Expand $title" })
        }
    }
    if (expanded) content()
}

@Composable
private fun SettingsCard(content: @Composable () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(scheme.surface)
            .border(1.dp, scheme.outline.copy(alpha = 0.35f), RoundedCornerShape(16.dp))
            .padding(16.dp)
    ) {
        content()
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
