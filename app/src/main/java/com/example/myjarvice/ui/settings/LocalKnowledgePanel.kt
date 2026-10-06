package com.example.myjarvice.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoStories
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.myjarvice.ui.JarvisIconBadge

@Composable
fun LocalKnowledgePanel() {
    var open by remember { mutableStateOf(false) }
    OutlinedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            JarvisIconBadge(Icons.Rounded.AutoStories)
            Text("Memory & documents", style = MaterialTheme.typography.titleMedium)
            Text("Inspect, edit or exclude what Jarvis can retrieve. Your library stays on this phone.", style = MaterialTheme.typography.bodyMedium)
            Button(onClick = { open = true }) { Text("Manage library") }
        }
    }
    if (open) KnowledgeLibraryDialog(onDismiss = { open = false })
}
