package com.example.myjarvice.ui.main

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.example.myjarvice.data.ConnectionStatus
import com.example.myjarvice.data.SmartMode

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ResponseModeSheet(
    mode: SmartMode,
    connection: ConnectionStatus,
    hasLocalModel: Boolean,
    onSelect: (SmartMode) -> Unit,
    onConnect: () -> Unit,
    onDismiss: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp).padding(bottom = 24.dp)
        ) {
            Text("Choose how Jarvis responds", style = MaterialTheme.typography.headlineSmall)
            Text(
                "Switch anytime. Jarvis remembers your choice for the next message.",
                style = MaterialTheme.typography.bodyMedium,
                color = colors.onSurfaceVariant,
                modifier = Modifier.padding(top = 6.dp, bottom = 18.dp)
            )
            listOf(SmartMode.AUTO, SmartMode.FAST_ON_DEVICE, SmartMode.STRONG_HOST).forEach { value ->
                val presentation = responseModeUi(value, connection, hasLocalModel)
                val selected = mode == value
                Surface(
                    shape = RoundedCornerShape(22.dp),
                    color = if (selected) colors.secondaryContainer else colors.surfaceContainerLow,
                    border = BorderStroke(1.dp, if (selected) colors.primary else colors.outlineVariant),
                    modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp)
                ) {
                    Row(
                        Modifier.selectable(
                            selected = selected,
                            role = Role.RadioButton,
                            onClick = { onSelect(value) }
                        ).padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = if (selected) colors.primary else colors.surfaceContainerHighest,
                            modifier = Modifier.size(42.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    presentation.compactLabel.take(1),
                                    style = MaterialTheme.typography.titleMedium,
                                    color = if (selected) colors.onPrimary else colors.onSurfaceVariant
                                )
                            }
                        }
                        Column(Modifier.weight(1f)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(start = 12.dp)
                            ) {
                                Text(
                                    presentation.title,
                                    style = MaterialTheme.typography.titleMedium,
                                    modifier = Modifier.weight(1f)
                                )
                                if (presentation.recommended) StatusPill("Recommended", selected)
                            }
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(start = 12.dp, top = 5.dp)
                            ) {
                                Surface(
                                    shape = RoundedCornerShape(50),
                                    color = when {
                                        value == SmartMode.FAST_ON_DEVICE && !hasLocalModel -> colors.error
                                        value == SmartMode.STRONG_HOST && connection != ConnectionStatus.CONNECTED -> colors.outline
                                        else -> colors.tertiary
                                    },
                                    modifier = Modifier.size(7.dp)
                                ) {}
                                Text(
                                    presentation.status,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = colors.onSurfaceVariant,
                                    modifier = Modifier.padding(start = 7.dp)
                                )
                            }
                            Text(
                                presentation.description,
                                style = MaterialTheme.typography.bodySmall,
                                color = colors.onSurfaceVariant,
                                modifier = Modifier.padding(start = 12.dp, top = 7.dp)
                            )
                        }
                        RadioButton(selected = selected, onClick = null, modifier = Modifier.padding(start = 4.dp))
                    }
                }
            }
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = colors.surfaceContainer,
                modifier = Modifier.fillMaxWidth().padding(top = 2.dp)
            ) {
                Text(
                    "Privacy note · ‘On this phone’ applies to text generation. Online speech or voice services may still use the internet when enabled.",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onSurfaceVariant,
                    modifier = Modifier.padding(14.dp)
                )
            }
            OutlinedButton(
                onClick = onConnect,
                modifier = Modifier.fillMaxWidth().padding(top = 14.dp).heightIn(min = 48.dp)
            ) { Text("PC connection settings") }
        }
    }
}

@Composable
private fun StatusPill(label: String, selected: Boolean) {
    val colors = MaterialTheme.colorScheme
    Surface(
        shape = RoundedCornerShape(50),
        color = if (selected) colors.primary.copy(alpha = .13f) else colors.surfaceContainerHighest
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = if (selected) colors.primary else colors.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        )
    }
}
