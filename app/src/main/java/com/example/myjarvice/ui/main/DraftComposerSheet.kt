package com.example.myjarvice.ui.main

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.example.myjarvice.data.DraftTone
import com.example.myjarvice.data.WritingProfile
import com.example.myjarvice.data.WritingProfileStore
import com.example.myjarvice.data.WritingDraftPrompt

/** Prepares an editable chat prompt; opening or completing this form never sends a message. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
internal fun DraftComposerSheet(onDismiss: () -> Unit, onPrepare: (String) -> Unit) {
    val context = LocalContext.current
    val profileStore = remember { WritingProfileStore(context.applicationContext) }
    val initialProfile = remember { profileStore.load() }
    WritingDraftSheetContent(initialProfile, profileStore::save, onDismiss, onPrepare)
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
internal fun WritingDraftSheetContent(initialProfile: WritingProfile, onRememberProfile: (WritingProfile) -> Unit,
    onDismiss: () -> Unit, onPrepare: (String) -> Unit) {
    var savedProfile by remember { mutableStateOf(initialProfile) }
    var recipient by rememberSaveable { mutableStateOf("") }
    var intent by rememberSaveable { mutableStateOf("") }
    var useProfile by rememberSaveable { mutableStateOf(savedProfile.enabled) }
    var tone by rememberSaveable { mutableStateOf(if (savedProfile.enabled) savedProfile.tone else DraftTone.NATURAL) }
    var rememberedTone by remember { mutableStateOf(false) }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.fillMaxWidth().imePadding().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp).padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text("Find the right words", style = MaterialTheme.typography.headlineSmall)
            Text("Prepare a draft request, then review it in chat. You decide what to send and where.",
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            OutlinedTextField(recipient, { recipient = it.take(WritingDraftPrompt.MAX_RECIPIENT) }, label = { Text("Who is it for? (optional)") },
                placeholder = { Text("A friend, my manager…") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(intent, { intent = it.take(WritingDraftPrompt.MAX_INTENT) }, label = { Text("What do you want to say?") },
                minLines = 3, maxLines = 5, modifier = Modifier.fillMaxWidth())
            Text("Make it sound", style = MaterialTheme.typography.titleSmall)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                DraftTone.entries.forEach { choice ->
                    FilterChip(selected = tone == choice, onClick = { tone = choice; rememberedTone = false }, label = { Text(choice.label) })
                }
            }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f).padding(end = 12.dp)) {
                    Text("Use my writing profile", style = MaterialTheme.typography.titleSmall)
                    Text(if (useProfile) "${savedProfile.length.label} · ${if (savedProfile.emoji) "Emoji allowed" else "No emoji"}"
                        else "Only the tone selected above is used", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(useProfile, { useProfile = it }, modifier = Modifier.semantics { contentDescription = "Use my writing profile" })
            }
            Text("Personalize length, wording and sign-off in Settings → Writing style.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            TextButton(onClick = {
                savedProfile = savedProfile.copy(enabled = true, tone = tone)
                onRememberProfile(savedProfile)
                useProfile = true
                rememberedTone = true
            }, enabled = !savedProfile.enabled || savedProfile.tone != tone) {
                Text(if (rememberedTone) "Tone remembered" else "Remember this tone for future drafts")
            }
            if (useProfile) Text("Your writing preferences will be included in the editable request. Using PC mode sends that request to your paired PC.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Button(onClick = {
                onPrepare(WritingDraftPrompt.build(recipient, intent, tone,
                    if (useProfile) savedProfile.copy(enabled = true) else null))
            }, enabled = intent.isNotBlank(), modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("Prepare in chat") }
        }
    }
}
