package com.example.myjarvice.ui.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.myjarvice.data.KnowledgeEntry
import com.example.myjarvice.data.LocalKnowledgeStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun LocalKnowledgePanel() {
    val context = LocalContext.current
    val store = remember { LocalKnowledgeStore(context.applicationContext) }
    val scope = rememberCoroutineScope()
    var entries by remember { mutableStateOf(emptyList<KnowledgeEntry>()) }
    var fact by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("") }
    var pendingDelete by remember { mutableStateOf<KnowledgeEntry?>(null) }
    fun perform(successMessage: String = "Saved on this phone.", clearFact: Boolean = false, operation: () -> Unit) {
        busy = true
        scope.launch {
            val result = withContext(Dispatchers.IO) { runCatching { operation(); store.entries() } }
            result.onSuccess { entries = it; status = successMessage; if (clearFact) fact = "" }
                .onFailure { status = it.message ?: "Couldn't update local knowledge." }
            busy = false
        }
    }
    LaunchedEffect(Unit) {
        val result = withContext(Dispatchers.IO) { runCatching { store.entries() } }
        result.onSuccess { entries = it }.onFailure { status = "Couldn't read local knowledge: ${it.message}" }
    }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) perform { store.importDocument(uri) }
    }
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Make Jarvis yours", style = MaterialTheme.typography.titleMedium)
            Text("Save a preference or bring your notes. Jarvis can use them in local replies.", style = MaterialTheme.typography.bodyMedium)
            Text("Private to this phone · never added to server requests", style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary)
            OutlinedTextField(value = fact, onValueChange = { fact = it.take(300) },
                label = { Text("A fact to remember") }, supportingText = { Text("${fact.length}/300") },
                modifier = Modifier.fillMaxWidth(), enabled = !busy)
            Button(enabled = !busy && fact.isNotBlank(), onClick = {
                val savedFact = fact
                perform(clearFact = true) { store.remember(savedFact) }
            }) { Text("Save memory") }
            OutlinedButton(enabled = !busy, onClick = { picker.launch(arrayOf("*/*")) }) {
                Text("Import PDF / TXT / Markdown")
            }
            Text("PDF, TXT or Markdown · up to 5 MB and 50 pages. Text-based PDFs only.", style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            if (status.isNotBlank()) Text(status, style = MaterialTheme.typography.bodySmall)
            Text("${entries.count { it.memory }} memories · ${entries.count { !it.memory }} documents")
            if (entries.isEmpty()) Text("Nothing saved yet. Try ‘I prefer short answers’ above.",
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            entries.forEach { entry ->
                HorizontalDivider()
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(if (entry.memory) "MEMORY" else "DOCUMENT", style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary)
                        Text(if (entry.memory) entry.text else entry.name, maxLines = 4, overflow = TextOverflow.Ellipsis)
                    }
                    TextButton(enabled = !busy, onClick = { pendingDelete = entry }) { Text("Delete") }
                }
            }
        }
    }
    pendingDelete?.let { entry ->
        AlertDialog(onDismissRequest = { pendingDelete = null },
            title = { Text("Delete saved ${if (entry.memory) "memory" else "document"}?") },
            text = { Text("Removes this entry from Jarvis. Original files and existing chat messages are unchanged.") },
            confirmButton = { TextButton(onClick = { pendingDelete = null; perform("Removed from Jarvis.") { store.delete(entry.id) } }) { Text("Delete") } },
            dismissButton = { TextButton(onClick = { pendingDelete = null }) { Text("Cancel") } })
    }
}
