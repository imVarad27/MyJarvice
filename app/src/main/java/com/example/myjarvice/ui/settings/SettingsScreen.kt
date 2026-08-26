package com.example.myjarvice.ui.settings

import android.os.Build
import android.widget.Toast
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
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.myjarvice.data.ChatHistoryStore
import com.example.myjarvice.data.SettingsStore
import com.example.myjarvice.data.SpeechManager
import com.example.myjarvice.theme.ArcGold
import com.example.myjarvice.theme.JarvisCyan
import com.example.myjarvice.theme.ThemeMode
import com.example.myjarvice.ui.icons.IconActivity
import com.example.myjarvice.ui.icons.IconDocument
import com.example.myjarvice.ui.icons.IconMicrophone
import com.example.myjarvice.ui.icons.IconSettings
import com.example.myjarvice.ui.icons.IconSparkles
import com.example.myjarvice.ui.icons.IconSpeaker
import com.example.myjarvice.ui.icons.IconTrash
import com.example.myjarvice.ui.icons.IconVoiceWaveform
import kotlin.math.abs

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
    val historyStore = remember { ChatHistoryStore(context) }

    var selectedVoiceId by remember { mutableStateOf(settingsStore.ttsVoice) }
    var speechRate by remember { mutableFloatStateOf(settingsStore.ttsSpeechRate) }
    var pitch by remember { mutableFloatStateOf(settingsStore.ttsPitch) }
    var autoSpeak by remember { mutableStateOf(settingsStore.autoSpeakReplies) }
    var userName by remember { mutableStateOf(settingsStore.userName) }
    var aiPersonality by remember { mutableStateOf(settingsStore.aiPersonality) }
    var modelName by remember { mutableStateOf(settingsStore.modelName) }
    var temperature by remember { mutableFloatStateOf(settingsStore.temperature) }
    var serverIp by remember { mutableStateOf(settingsStore.serverIp) }
    var serverToken by remember { mutableStateOf(settingsStore.serverToken) }

    var voiceMatchEnabled by remember { mutableStateOf(settingsStore.voiceMatchEnabled) }
    var voiceMatchThreshold by remember { mutableFloatStateOf(settingsStore.voiceMatchThreshold) }
    var isEnrolled by remember { mutableStateOf(settingsStore.isVoiceProfileEnrolled) }

    var showVoiceDropdown by remember { mutableStateOf(false) }
    var showPersonalityDropdown by remember { mutableStateOf(false) }

    val scheme = MaterialTheme.colorScheme

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(scheme.background)
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp)
    ) {
        // Top Bar
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
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
                    "JARVIS 1.0 • System Preferences",
                    color = scheme.onSurfaceVariant,
                    fontSize = 12.sp
                )
            }
        }

        Spacer(Modifier.height(24.dp))

        // ==========================================
        // 1. APPEARANCE & THEME
        // ==========================================
        SettingsSectionHeader("APPEARANCE")

        SettingsCard {
            Text("Theme Mode", color = scheme.onSurface, fontSize = 14.sp, fontWeight = FontWeight.Medium)
            Spacer(Modifier.height(10.dp))

            Row(
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
                    Column(Modifier.weight(1f)) {
                        Text("Dynamic Material You Color", color = scheme.onSurface, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                        Text("Tint UI with your system wallpaper palette", color = scheme.onSurfaceVariant, fontSize = 12.sp)
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

        // ==========================================
        // 2. VOICE & SPEECH ENGINE
        // ==========================================
        SettingsSectionHeader("VOICE & SPEECH")

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
                    speechManager.voices.value.forEach { v ->
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
                    speechManager.previewVoice(selectedVoiceId, "Greetings, Sir. JARVIS voice engine calibrated.")
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
                Column(Modifier.weight(1f)) {
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

        // ==========================================
        // 3. WAKE WORD & VOICE MATCH BIOMETRICS
        // ==========================================
        SettingsSectionHeader("WAKE WORD & BIOMETRICS")

        SettingsCard {
            // Wake Word Toggle
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.weight(1f)) {
                    Text("\"Hey Jarvis\" Wake Sentinel", color = scheme.onSurface, fontSize = 14.sp, fontWeight = FontWeight.Medium)
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
                Column(Modifier.weight(1f)) {
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

        // ==========================================
        // 4. MODEL & INTELLIGENCE
        // ==========================================
        SettingsSectionHeader("MODEL & INTELLIGENCE")

        SettingsCard {
            OutlinedTextField(
                value = userName,
                onValueChange = {
                    userName = it
                    settingsStore.userName = it
                },
                label = { Text("Your Preferred Name / Title") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = scheme.primary,
                    unfocusedBorderColor = scheme.outline.copy(alpha = 0.4f)
                )
            )

            Spacer(Modifier.height(12.dp))

            // AI Personality
            Text("AI Personality", color = scheme.onSurface, fontSize = 13.sp, fontWeight = FontWeight.Medium)
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
                    Text(aiPersonality, color = scheme.onSurface, fontSize = 13.sp)
                    Text("▾", color = scheme.onSurfaceVariant, fontSize = 12.sp)
                }

                DropdownMenu(
                    expanded = showPersonalityDropdown,
                    onDismissRequest = { showPersonalityDropdown = false },
                    modifier = Modifier.background(scheme.surface)
                ) {
                    listOf(
                        "Iron Man JARVIS (Polite, Stark HUD)",
                        "Concise Assistant (Brief, direct)",
                        "Technical Specialist (Deep reasoning)"
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

            OutlinedTextField(
                value = modelName,
                onValueChange = {
                    modelName = it
                    settingsStore.modelName = it
                },
                label = { Text("Ollama Local Model") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = scheme.primary,
                    unfocusedBorderColor = scheme.outline.copy(alpha = 0.4f)
                )
            )

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
        }

        Spacer(Modifier.height(24.dp))

        // ==========================================
        // 5. SERVER CONNECTION
        // ==========================================
        SettingsSectionHeader("HOST SERVER")

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
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = scheme.primary,
                    unfocusedBorderColor = scheme.outline.copy(alpha = 0.4f)
                )
            )
        }

        Spacer(Modifier.height(24.dp))

        // ==========================================
        // 6. DATA & STORAGE
        // ==========================================
        SettingsSectionHeader("DATA & STORAGE")

        SettingsCard {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Local Drive RAG Engine", color = scheme.onSurface, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                    Text("Indexed across D: and E: drive documents", color = scheme.onSurfaceVariant, fontSize = 12.sp)
                }
                Text("Active", color = scheme.primary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            }

            Spacer(Modifier.height(14.dp))
            HorizontalDivider(color = scheme.outline.copy(alpha = 0.2f))
            Spacer(Modifier.height(10.dp))

            Button(
                onClick = {
                    historyStore.clearAll()
                    Toast.makeText(context, "Chat history wiped", Toast.LENGTH_SHORT).show()
                },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444).copy(alpha = 0.15f)),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                IconTrash(tint = Color(0xFFEF4444), size = 16.dp)
                Spacer(Modifier.width(8.dp))
                Text("Clear All Chat Sessions", color = Color(0xFFEF4444), fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            }
        }

        Spacer(Modifier.height(24.dp))

        // ==========================================
        // 7. ABOUT
        // ==========================================
        SettingsSectionHeader("ABOUT")

        SettingsCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconSparkles(tint = scheme.primary, size = 20.dp)
                Spacer(Modifier.width(10.dp))
                Column {
                    Text("JARVIS 1.0", color = scheme.onSurface, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    Text("Neural Core • Google Gemma 4 7.5B Q4_0", color = scheme.onSurfaceVariant, fontSize = 12.sp)
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(
                "Offline voice recognition, speaker verification & full-drive RAG intelligence.",
                color = scheme.onSurfaceVariant,
                fontSize = 11.sp,
                lineHeight = 16.sp
            )
        }

        Spacer(Modifier.height(30.dp))
    }
}

@Composable
private fun SettingsSectionHeader(title: String) {
    Text(
        title,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        fontFamily = FontFamily.Monospace,
        letterSpacing = 1.sp,
        modifier = Modifier.padding(horizontal = 4.dp, vertical = 6.dp)
    )
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
private fun ThemeChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    val bg = if (selected) scheme.primary.copy(alpha = 0.2f) else scheme.surfaceVariant
    val border = if (selected) scheme.primary else scheme.outline.copy(alpha = 0.3f)
    val text = if (selected) scheme.primary else scheme.onSurface

    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(bg)
            .border(1.dp, border, RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(label, color = text, fontSize = 12.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal)
    }
}
