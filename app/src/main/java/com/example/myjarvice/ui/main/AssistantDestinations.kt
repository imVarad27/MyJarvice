package com.example.myjarvice.ui.main

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.BookmarkBorder
import androidx.compose.material.icons.rounded.Today
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AssistantTodayDialog(onDismiss: () -> Unit, content: @Composable () -> Unit) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Scaffold(topBar = {
            TopAppBar(title = { Text("Today") }, navigationIcon = {
                IconButton(onClick = onDismiss) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back to chat") }
            })
        }) { padding -> Box(Modifier.fillMaxSize().padding(padding)) { content() } }
    }
}

/** Discoverable destinations without loading any personal data until the user opens them. */
@Composable
internal fun DrawerAssistantLinks(onToday: () -> Unit, onSaved: () -> Unit) {
    Column {
    TextButton(onClick = onToday, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
        Icon(Icons.Rounded.Today, null, modifier = Modifier.size(20.dp))
        Text("Today", modifier = Modifier.weight(1f).padding(start = 12.dp))
    }
    TextButton(onClick = onSaved, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
        Icon(Icons.Rounded.BookmarkBorder, null, modifier = Modifier.size(20.dp))
        Text("Saved items", modifier = Modifier.weight(1f).padding(start = 12.dp))
    }
    }
}
