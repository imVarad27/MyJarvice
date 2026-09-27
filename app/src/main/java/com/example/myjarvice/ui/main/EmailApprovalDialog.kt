package com.example.myjarvice.ui.main

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.myjarvice.data.PendingEmail

/** The complete draft stays readable before the existing explicit send approval. */
@Composable
fun EmailApprovalDialog(draft: PendingEmail, onApprove: () -> Unit, onDiscard: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDiscard,
        title = { Text("Review email") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                FieldRow("To", draft.to)
                FieldRow("Subject", draft.subject)
                Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceContainerLow) {
                    SelectionContainer {
                        Text(draft.body, modifier = Modifier.fillMaxWidth().padding(16.dp),
                            style = MaterialTheme.typography.bodyLarge)
                    }
                }
                Text("Nothing is sent until you approve. Approval is one-time and expires if you wait too long.", style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        confirmButton = { Button(onClick = onApprove) { Text("Approve & send") } },
        dismissButton = { TextButton(onClick = onDiscard) { Text("Discard") } }
    )
}

@Composable
private fun FieldRow(label: String, value: String) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        SelectionContainer { Text(value, style = MaterialTheme.typography.bodyLarge) }
    }
}
