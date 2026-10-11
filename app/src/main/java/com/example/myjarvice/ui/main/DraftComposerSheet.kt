package com.example.myjarvice.ui.main

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.PersonOutline
import com.example.myjarvice.data.DraftContactRepository
import com.example.myjarvice.data.DraftTone
import com.example.myjarvice.data.WritingProfile
import com.example.myjarvice.data.WritingProfileStore
import com.example.myjarvice.data.WritingDraftPrompt
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Prepares an editable chat prompt; opening or completing this form never sends a message. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
internal fun DraftComposerSheet(onDismiss: () -> Unit, onPrepare: (String) -> Unit) {
    val context = LocalContext.current
    val profileStore = remember { WritingProfileStore(context.applicationContext) }
    val initialProfile = remember { profileStore.load() }
    val contacts = remember { DraftContactRepository(context.applicationContext) }
    val scope = rememberCoroutineScope()
    // Names and contact URIs are not retained in saved-instance state, preferences or backups.
    var recipient by remember { mutableStateOf("") }
    var contactBusy by remember { mutableStateOf(false) }
    var contactError by remember { mutableStateOf<String?>(null) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickContact()) { uri ->
        if (uri == null) {
            contactBusy = false // Cancel leaves the previous recipient and draft untouched.
        } else scope.launch {
            contactBusy = true
            try {
                recipient = withContext(Dispatchers.IO) { contacts.selectedName(uri) }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { contactError = "Couldn't read that contact's name. Try again or type it below." }
            finally { contactBusy = false }
        }
    }
    WritingDraftSheetContent(initialProfile, profileStore::save, onDismiss, onPrepare,
        recipient, { recipient = it; contactError = null }, onPickContact = {
            contactError = null; contactBusy = true
            try { picker.launch(null) }
            catch (_: Exception) {
                contactBusy = false
                contactError = "No contact picker is available. You can still type a recipient below."
            }
        }, contactBusy = contactBusy, contactError = contactError)
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
internal fun WritingDraftSheetContent(initialProfile: WritingProfile, onRememberProfile: (WritingProfile) -> Unit,
    onDismiss: () -> Unit, onPrepare: (String) -> Unit, recipient: String,
    onRecipientChange: (String) -> Unit, onPickContact: () -> Unit,
    contactBusy: Boolean = false, contactError: String? = null) {
    var savedProfile by remember { mutableStateOf(initialProfile) }
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
            OutlinedTextField(recipient, { onRecipientChange(it.take(WritingDraftPrompt.MAX_RECIPIENT)) }, label = { Text("Who is it for? (optional)") },
                placeholder = { Text("A friend, my manager…") }, singleLine = true, enabled = !contactBusy,
                trailingIcon = {
                    if (recipient.isNotEmpty()) IconButton(onClick = { onRecipientChange("") }, enabled = !contactBusy) {
                        Icon(Icons.Rounded.Close, contentDescription = "Clear recipient")
                    }
                }, modifier = Modifier.fillMaxWidth())
            OutlinedButton(onClick = onPickContact, enabled = !contactBusy, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                Icon(Icons.Rounded.PersonOutline, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(if (contactBusy) "Choosing a contact…" else "Choose from contacts")
            }
            if (contactBusy) LinearProgressIndicator(Modifier.fillMaxWidth())
            Text("Only the selected name is read—not phone numbers or email addresses. You can edit or clear it.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            contactError?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
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
            Text("The recipient and message will appear in your editable chat request. Nothing is generated yet. Sending that request in PC mode shares it with your paired PC.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (useProfile) Text("Your enabled writing preferences are included in the request too.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Button(onClick = {
                onPrepare(WritingDraftPrompt.build(recipient, intent, tone,
                    if (useProfile) savedProfile.copy(enabled = true) else null))
            }, enabled = intent.isNotBlank() && !contactBusy, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("Prepare in chat") }
        }
    }
}
