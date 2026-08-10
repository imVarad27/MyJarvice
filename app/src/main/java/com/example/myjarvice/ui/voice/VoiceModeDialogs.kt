package com.example.myjarvice.ui.voice

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.myjarvice.data.ConnectionStatus
import com.example.myjarvice.data.VoiceOption
import com.example.myjarvice.theme.JarvisCyan
import com.example.myjarvice.theme.JarvisSurfaceDark
import com.example.myjarvice.theme.TextPrimary
import com.example.myjarvice.theme.TextSecondary

/** Session details behind voice mode's ⓘ button. */
@Composable
fun VoiceInfoDialog(
    serverIp: String,
    connectionStatus: ConnectionStatus,
    messageCount: Int,
    voiceLabel: String,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Session Info", color = JarvisCyan, fontFamily = FontFamily.Monospace) },
        text = {
            Column {
                InfoRow("Host", "$serverIp:8000")
                InfoRow("Link", connectionStatus.name)
                InfoRow("Messages", messageCount.toString())
                InfoRow("Voice", voiceLabel)
                Spacer(Modifier.size(10.dp))
                Text(
                    "Speak naturally — JARVICE listens again automatically after each reply.",
                    color = TextSecondary,
                    fontSize = 12.sp
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Close", color = JarvisCyan) }
        },
        containerColor = JarvisSurfaceDark
    )
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, color = TextSecondary, fontSize = 13.sp, fontFamily = FontFamily.Monospace)
        Text(value, color = TextPrimary, fontSize = 13.sp, fontFamily = FontFamily.Monospace)
    }
}

/** Voice picker behind the sliders button. Selecting applies immediately and persists. */
@Composable
fun VoicePickerDialog(
    voices: List<VoiceOption>,
    selectedVoiceId: String,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Change Voice", color = JarvisCyan, fontFamily = FontFamily.Monospace) },
        text = {
            if (voices.isEmpty()) {
                Text(
                    "No offline English voices are installed on this device. " +
                        "Add one under Settings › Accessibility › Text-to-speech.",
                    color = TextSecondary,
                    fontSize = 13.sp
                )
            } else {
                LazyColumn(modifier = Modifier.heightIn(max = 320.dp)) {
                    item {
                        VoiceRow(
                            label = "Engine default",
                            selected = selectedVoiceId.isBlank(),
                            onClick = { onSelect("") }
                        )
                    }
                    items(voices) { voice ->
                        VoiceRow(
                            label = voice.label,
                            selected = voice.id == selectedVoiceId,
                            onClick = { onSelect(voice.id) }
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Done", color = JarvisCyan) }
        },
        containerColor = JarvisSurfaceDark
    )
}

@Composable
private fun VoiceRow(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(16.dp)
                .clip(CircleShape)
                .background(if (selected) JarvisCyan else Color.Transparent)
        ) {
            if (!selected) {
                Box(
                    modifier = Modifier
                        .size(16.dp)
                        .clip(CircleShape)
                        .background(TextSecondary.copy(alpha = 0.25f))
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        Text(
            label,
            color = if (selected) TextPrimary else TextSecondary,
            fontSize = 14.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
        )
    }
}
