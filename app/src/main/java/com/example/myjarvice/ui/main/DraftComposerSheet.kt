package com.example.myjarvice.ui.main

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/** Prepares an editable chat prompt; opening or completing this form never sends a message. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
internal fun DraftComposerSheet(onDismiss: () -> Unit, onPrepare: (String) -> Unit) {
    var recipient by rememberSaveable { mutableStateOf("") }
    var intent by rememberSaveable { mutableStateOf("") }
    var tone by rememberSaveable { mutableStateOf("Natural") }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.fillMaxWidth().imePadding().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp).padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text("Find the right words", style = MaterialTheme.typography.headlineSmall)
            Text("Prepare a draft request, then review it in chat. You decide what to send and where.",
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            OutlinedTextField(recipient, { recipient = it.take(120) }, label = { Text("Who is it for? (optional)") },
                placeholder = { Text("A friend, my manager…") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(intent, { intent = it.take(2400) }, label = { Text("What do you want to say?") },
                minLines = 3, maxLines = 5, modifier = Modifier.fillMaxWidth())
            Text("Make it sound", style = MaterialTheme.typography.titleSmall)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("Natural", "Warm", "Professional", "Brief").forEach { choice ->
                    FilterChip(selected = tone == choice, onClick = { tone = choice }, label = { Text(choice) })
                }
            }
            Button(onClick = {
                onPrepare(buildString {
                    append("Write a $tone-sounding message draft")
                    if (recipient.isNotBlank()) append(" for ${recipient.trim()}")
                    append(". Only draft the text; do not send it or call any sending tools. ")
                    append("Don't invent facts or promises. Ask if an essential detail is missing.\n\nWhat I want to say:\n")
                    append(intent.trim())
                })
            }, enabled = intent.isNotBlank(), modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("Prepare in chat") }
        }
    }
}
