package com.example.myjarvice.ui.main

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.selection.selectable
import androidx.compose.ui.unit.dp
import com.example.myjarvice.data.ConnectionStatus
import com.example.myjarvice.data.SmartMode

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ResponseModeSheet(mode: SmartMode, connection: ConnectionStatus, hasLocalModel: Boolean,
    onSelect: (SmartMode) -> Unit, onConnect: () -> Unit, onDismiss: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp).padding(bottom = 24.dp)) {
            Text("Choose AI model", style = MaterialTheme.typography.headlineMedium)
            Text("Choose where Jarvis runs. Your choice is saved as the default.", style = MaterialTheme.typography.bodyMedium,
                color = colors.onSurfaceVariant, modifier = Modifier.padding(top = 6.dp, bottom = 20.dp))
            listOf(
                Triple(SmartMode.AUTO, "Auto", "Use your PC when connected. Fall back to the phone model when available."),
                Triple(SmartMode.FAST_ON_DEVICE, "Phone model", "Text replies stay on this device. ${if (hasLocalModel) "Imported model ready." else "Import a model in Settings first."}"),
                Triple(SmartMode.STRONG_HOST, "PC model", "Use the model configured on your paired PC, web search and PC tools. ${if (connection == ConnectionStatus.CONNECTED) "Connected." else "PC connection required."}")
            ).forEach { (value, title, detail) ->
                Surface(shape = RoundedCornerShape(16.dp), color = if (mode == value) colors.secondaryContainer else colors.surfaceContainerLow,
                    modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp)) {
                    Row(Modifier.selectable(selected = mode == value, role = Role.RadioButton, onClick = { onSelect(value) })
                        .padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(title, style = MaterialTheme.typography.titleMedium)
                            Text(detail, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant,
                                modifier = Modifier.padding(top = 4.dp))
                        }
                        RadioButton(selected = mode == value, onClick = null)
                    }
                }
            }
            Text("Phone mode describes text generation. Speech recognition and optional neural voices may use the internet.",
                style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
            OutlinedButton(onClick = onConnect, modifier = Modifier.fillMaxWidth().padding(top = 16.dp)) { Text("Manage PC connection") }
        }
    }
}
