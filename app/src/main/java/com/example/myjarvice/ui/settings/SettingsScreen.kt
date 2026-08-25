package com.example.myjarvice.ui.settings

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.myjarvice.data.SettingsStore
import com.example.myjarvice.theme.ThemeMode
import kotlin.math.abs

/**
 * Appearance-focused settings for this increment: theme mode + Material You.
 * Full settings (font size, model, memory, export/import) arrive in a later phase.
 */
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
    var voiceMatchEnabled by remember { mutableStateOf(settingsStore.voiceMatchEnabled) }
    var voiceMatchThreshold by remember { mutableFloatStateOf(settingsStore.voiceMatchThreshold) }
    var isEnrolled by remember { mutableStateOf(settingsStore.isVoiceProfileEnrolled) }


    val scheme = MaterialTheme.colorScheme

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(scheme.background)
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp)
    ) {
        // Top bar
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .clickable { onBack() },
                contentAlignment = Alignment.Center
            ) {
                Text("←", color = scheme.onBackground, fontSize = 24.sp)
            }
            Spacer(Modifier.size(8.dp))
            Text(
                "Settings",
                color = scheme.onBackground,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(Modifier.height(24.dp))

        SectionLabel("APPEARANCE")
        Spacer(Modifier.height(12.dp))

        // Theme mode selector
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(scheme.surface)
                .border(1.dp, scheme.outline.copy(alpha = 0.4f), RoundedCornerShape(16.dp))
                .padding(14.dp)
        ) {
            Text("Theme", color = scheme.onSurface, fontSize = 15.sp, fontWeight = FontWeight.Medium)
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ThemeMode.entries.forEach { mode ->
                    ThemeChip(
                        label = mode.label,
                        selected = mode == themeMode,
                        modifier = Modifier.weight(1f),
                        onClick = { onThemeMode(mode) }
                    )
                }
            }
        }

        Spacer(Modifier.height(14.dp))

        // Dynamic color (Material You)
        val dynamicSupported = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(scheme.surface)
                .border(1.dp, scheme.outline.copy(alpha = 0.4f), RoundedCornerShape(16.dp))
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    "Material You color",
                    color = if (dynamicSupported) scheme.onSurface else scheme.onSurfaceVariant,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    if (dynamicSupported) "Match your wallpaper palette" else "Requires Android 12+",
                    color = scheme.onSurfaceVariant,
                    fontSize = 12.sp
                )
            }
            Switch(
                checked = dynamicColor && dynamicSupported,
                enabled = dynamicSupported,
                onCheckedChange = onDynamicColor,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = scheme.onPrimary,
                    checkedTrackColor = scheme.primary
                )
            )
        }

        Spacer(Modifier.height(24.dp))
        SectionLabel("WAKE WORD")
        Spacer(Modifier.height(12.dp))
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(scheme.surface)
                .border(1.dp, scheme.outline.copy(alpha = 0.4f), RoundedCornerShape(16.dp))
                .padding(14.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.weight(1f)) {
                    Text("\"Hey Jarvis\" activation", color = scheme.onSurface, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                    Text(
                        "Always-on background listening",
                        color = scheme.onSurfaceVariant,
                        fontSize = 12.sp
                    )
                }
                Switch(
                    checked = wakeEnabled,
                    onCheckedChange = onWakeEnabled,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = scheme.onPrimary,
                        checkedTrackColor = scheme.primary
                    )
                )
            }
            Spacer(Modifier.height(8.dp))
            Text(
                "Fully offline — no cloud audio sent. Operates locally via Vosk Sentinel.",
                color = scheme.onSurfaceVariant,
                fontSize = 11.sp
            )
        }

        Spacer(Modifier.height(24.dp))
        SectionLabel("VOICE MATCH BIOMETRICS")
        Spacer(Modifier.height(12.dp))
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(scheme.surface)
                .border(1.dp, scheme.outline.copy(alpha = 0.4f), RoundedCornerShape(16.dp))
                .padding(16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Only respond to my voice", color = scheme.onSurface, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                    }
                    Text(
                        if (isEnrolled) "Status: Profile Calibrated (Biometric Active)" else "Status: Not Calibrated",
                        color = if (isEnrolled) com.example.myjarvice.theme.JarvisCyan else scheme.onSurfaceVariant,
                        fontSize = 12.sp,
                        fontWeight = if (isEnrolled) FontWeight.SemiBold else FontWeight.Normal
                    )
                }
                Switch(
                    checked = voiceMatchEnabled && isEnrolled,
                    enabled = isEnrolled,
                    onCheckedChange = {
                        voiceMatchEnabled = it
                        settingsStore.voiceMatchEnabled = it
                    },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = scheme.onPrimary,
                        checkedTrackColor = scheme.primary
                    )
                )
            }

            Spacer(Modifier.height(14.dp))

            // Calibration Button
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(scheme.primary.copy(alpha = 0.15f))
                    .border(1.dp, scheme.primary.copy(alpha = 0.6f), RoundedCornerShape(12.dp))
                    .clickable { onOpenVoiceMatch() },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    if (isEnrolled) "⚡ Retrain Voice Model" else "🎙️ Calibrate My Voice (3 Steps)",
                    color = scheme.primary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            if (isEnrolled) {
                Spacer(Modifier.height(16.dp))
                Text(
                    "Verification Sensitivity: ${if (voiceMatchThreshold < 0.70f) "Lenient" else if (voiceMatchThreshold < 0.78f) "Standard (Recommended)" else "Strict"}",
                    color = scheme.onSurface,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium
                )
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(
                        "Lenient" to 0.66f,
                        "Standard" to 0.72f,
                        "Strict" to 0.80f
                    ).forEach { (label, thresh) ->
                        val isSelected = kotlin.math.abs(voiceMatchThreshold - thresh) < 0.03f
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(36.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (isSelected) scheme.primary.copy(alpha = 0.2f) else scheme.surfaceVariant.copy(alpha = 0.4f))
                                .border(1.dp, if (isSelected) scheme.primary else scheme.outline.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
                                .clickable {
                                    voiceMatchThreshold = thresh
                                    settingsStore.voiceMatchThreshold = thresh
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                label,
                                color = if (isSelected) scheme.primary else scheme.onSurfaceVariant,
                                fontSize = 11.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    }
                }

                Spacer(Modifier.height(14.dp))
                Text(
                    "Delete Voice Profile",
                    color = MaterialTheme.colorScheme.error,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.clickable {
                        settingsStore.clearVoiceProfile()
                        isEnrolled = false
                        voiceMatchEnabled = false
                    }
                )
            }
        }

        Spacer(Modifier.height(24.dp))
        SectionLabel("PREVIEW")
        Spacer(Modifier.height(12.dp))
        // Live swatch row so theme changes are immediately visible
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Swatch(scheme.primary, "Primary")
            Swatch(scheme.secondary, "Secondary")
            Swatch(scheme.tertiary, "Accent")
            Swatch(scheme.surfaceVariant, "Surface")
        }
    }
}


@Composable
private fun SectionLabel(text: String) {
    Text(
        text,
        color = MaterialTheme.colorScheme.primary,
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 2.sp
    )
}

@Composable
private fun ThemeChip(
    label: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    Box(
        modifier = modifier
            .height(40.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(if (selected) scheme.primary.copy(alpha = 0.18f) else scheme.surfaceVariant.copy(alpha = 0.5f))
            .border(
                1.dp,
                if (selected) scheme.primary else scheme.outline.copy(alpha = 0.4f),
                RoundedCornerShape(12.dp)
            )
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Text(
            label,
            color = if (selected) scheme.primary else scheme.onSurfaceVariant,
            fontSize = 12.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
        )
    }
}

@Composable
private fun Swatch(color: androidx.compose.ui.graphics.Color, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .size(52.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(color)
                .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
        )
        Spacer(Modifier.height(4.dp))
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 9.sp)
    }
}
