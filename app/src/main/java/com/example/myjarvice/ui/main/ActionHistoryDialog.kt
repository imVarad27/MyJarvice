package com.example.myjarvice.ui.main

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.myjarvice.data.ActionAuditEvent

@Composable
fun ActionHistoryDialog(
    events: List<ActionAuditEvent>,
    loading: Boolean,
    error: String?,
    onDismiss: () -> Unit,
    onRefresh: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Activity") },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    "Actions from your paired PC. Private file names, email text, and recipients are not kept here.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                when {
                    loading -> Text("Loading activity…", style = MaterialTheme.typography.bodyMedium)
                    error != null -> Text(error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
                    events.isEmpty() -> Text("No PC actions recorded yet.", style = MaterialTheme.typography.bodyMedium)
                    else -> events.forEachIndexed { index, event ->
                        if (index > 0) HorizontalDivider()
                        Column(Modifier.padding(vertical = 4.dp)) {
                            Text(event.summary, style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            Spacer(Modifier.height(3.dp))
                            Text(
                                "${event.actionType.replace('.', ' ')} · ${event.outcome.replace('_', ' ')} · ${event.createdAt.take(19).replace('T', ' ')}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onRefresh, enabled = !loading) { Text("Refresh") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Close") } }
    )
}
