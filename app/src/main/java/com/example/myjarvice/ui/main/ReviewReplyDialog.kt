package com.example.myjarvice.ui.main

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.SecureFlagPolicy
import com.example.myjarvice.data.LocalActionAuditStore
import com.example.myjarvice.data.ReplyShareApproval
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

// Only static metadata enters this ordered queue. Closing the editor does not cancel queued writes.
private val replyShareAuditScope = CoroutineScope(SupervisorJob() + Dispatchers.IO.limitedParallelism(1))

@Composable
internal fun ReviewReplyDialog(sourceText: String, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val audit = remember { LocalActionAuditStore(context.applicationContext) }
    var closeRequest by remember { mutableIntStateOf(0) }
    Dialog(onDismissRequest = { closeRequest++ },
        properties = DialogProperties(usePlatformDefaultWidth = false, securePolicy = SecureFlagPolicy.SecureOn)) {
        ReviewReplyContent(sourceText, onDismiss, onApprovedShare = { text ->
            // This callback is reachable only after the visible, exact-text approval below.
            runCatching { context.startActivity(replyShareChooser(text)) }.isSuccess
        }, onRecord = { outcome -> replyShareAuditScope.launch { audit.record("reply.share", outcome) } },
            closeRequest = closeRequest)
    }
}

/** Editing never invokes a model, changes the saved response, or shares data. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ReviewReplyContent(sourceText: String, onDismiss: () -> Unit,
    onApprovedShare: (String) -> Boolean, onRecord: (String) -> Unit = {}, closeRequest: Int = 0) {
    var text by remember(sourceText) { mutableStateOf(sourceText) }
    var pending by remember(sourceText) { mutableStateOf<ReplyShareApproval?>(null) }
    var inputError by remember(sourceText) { mutableStateOf<String?>(null) }
    var shareError by remember(sourceText) { mutableStateOf(false) }
    var opened by remember(sourceText) { mutableStateOf(false) }
    var discard by remember(sourceText) { mutableStateOf(false) }
    fun requestClose() {
        if (text != sourceText) discard = true else onDismiss()
    }
    fun cancelPreview() {
        pending?.cancel(); pending = null; onRecord("cancelled")
    }
    LaunchedEffect(closeRequest) { if (closeRequest > 0) requestClose() }
    // Edits/approvals are deliberately not saved across process/activity recreation.
    DisposableEffect(sourceText) { onDispose { pending?.cancel() } }
    Scaffold(modifier = Modifier.fillMaxSize().imePadding(), topBar = {
        TopAppBar(title = { Text("Review & share", maxLines = 1, overflow = TextOverflow.Ellipsis) }, navigationIcon = {
            IconButton(onClick = ::requestClose) { Icon(Icons.Rounded.Close, "Close reply review") }
        })
    }, bottomBar = {
        Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 20.dp, vertical = 12.dp)) {
            Button(onClick = {
                if (pending == null && ReplyShareApproval.validText(text) && inputError == null) {
                    pending = ReplyShareApproval.prepare(text)
                    onRecord("awaiting_approval")
                }
            }, enabled = ReplyShareApproval.validText(text) && inputError == null,
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                Icon(Icons.Rounded.Share, null); Spacer(Modifier.width(8.dp)); Text("Review sharing")
            }
        }
    }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text("Make it yours", style = MaterialTheme.typography.headlineSmall)
            Text("Edit this copy before it leaves Jarvis. Your saved reply stays unchanged.",
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            OutlinedTextField(text, { candidate ->
                pending?.cancel(); pending = null; opened = false; shareError = false
                if (candidate.length <= ReplyShareApproval.MAX_CHARS) { text = candidate; inputError = null }
                else inputError = "Text is too long. That change wasn't applied. Keep it within 20,000 characters."
            }, label = { Text("Text to share") }, minLines = 6, maxLines = 16,
                isError = inputError != null, modifier = Modifier.fillMaxWidth(),
                supportingText = { Text("${text.length} / ${ReplyShareApproval.MAX_CHARS} characters") })
            inputError?.let { Text(it, color = MaterialTheme.colorScheme.error,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }) }
            if (shareError) Text("Couldn't open the share sheet. Your edits are still here. Review again to retry.",
                color = MaterialTheme.colorScheme.error, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
            if (opened) Text("Share sheet opened. Jarvis cannot confirm whether you selected an app or sent anything.",
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }, color = MaterialTheme.colorScheme.primary)
            OutlinedCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("You're in control", style = MaterialTheme.typography.titleSmall)
                    Text("Only this text is shared after approval. Photos, audio, separate source chips and the rest of your conversation are not attached.",
                        style = MaterialTheme.typography.bodySmall)
                    Text("Choose the app and recipient yourself. Check the receiving app before sending; its behavior and data handling are outside Jarvis's control.",
                        style = MaterialTheme.typography.bodySmall)
                    Text("Edits stay here only. Closing or recreating this screen clears them. The assistant popup may close when another app opens. No model or PC is used for editing or opening the share sheet.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
    pending?.let { approval ->
        AlertDialog(onDismissRequest = ::cancelPreview,
            properties = DialogProperties(securePolicy = SecureFlagPolicy.SecureOn),
            title = { Text("Share this exact text?") },
            text = {
                Column(Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Approval passes the text below to Android's share sheet. You then choose where it goes. This is not a message-send confirmation.")
                    HorizontalDivider()
                    SelectionContainer { Text(approval.text) }
                }
            }, dismissButton = { TextButton(onClick = ::cancelPreview) { Text("Keep editing") } },
            confirmButton = { TextButton(onClick = {
                // Consume before the external call; double taps cannot reuse an approval.
                val approved = approval.consume(text)
                pending = null
                if (approved != null) {
                    onRecord("approved")
                    opened = onApprovedShare(approved)
                    shareError = !opened
                    onRecord(if (opened) "started" else "failed")
                }
            }) { Text("Approve & open share sheet") } })
    }
    if (discard) AlertDialog(onDismissRequest = { discard = false },
        properties = DialogProperties(securePolicy = SecureFlagPolicy.SecureOn), title = { Text("Discard your edits?") },
        text = { Text("Your saved chat reply won't change. This edited copy will be cleared.") },
        dismissButton = { TextButton(onClick = { discard = false }) { Text("Keep editing") } },
        confirmButton = { TextButton(onClick = { pending?.cancel(); onDismiss() }) { Text("Discard edits") } })
}
