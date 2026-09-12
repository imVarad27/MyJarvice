package com.example.myjarvice.ui.inbox

import android.Manifest
import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.media.MediaPlayer
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.myjarvice.data.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.DateFormat
import java.util.Calendar
import java.util.Date

/** Library and detail views share a responsive surface instead of stacking small dialogs. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RememberInboxScreen(store: RememberInboxStore, onDismiss: () -> Unit, onAskJarvis: (RememberItem) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var entries by remember { mutableStateOf<List<RememberItem>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var revision by remember { mutableIntStateOf(0) }
    var query by rememberSaveable { mutableStateOf("") }
    var kind by rememberSaveable { mutableStateOf<String?>(null) }
    var selectedId by rememberSaveable { mutableStateOf<String?>(null) }
    var deleteItem by remember { mutableStateOf<RememberItem?>(null) }
    var reminderItem by remember { mutableStateOf<RememberItem?>(null) }
    var pendingReminder by remember { mutableStateOf<Pair<String, Long>?>(null) }
    fun update(operation: () -> Unit) {
        scope.launch {
            runCatching { withContext(Dispatchers.IO) { operation() } }
                .onSuccess { revision++ }
                .onFailure { error = it.message ?: "Couldn't update your inbox. Try again." }
        }
    }
    val notifications = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        pendingReminder?.let { (id, time) ->
            if (granted) update { store.setReminder(id, time) }
            else error = "Notifications are off. Enable Jarvis notifications in Android settings to receive reminders."
        }
        pendingReminder = null
    }
    fun schedule(item: RememberItem, time: Long) {
        if (time <= System.currentTimeMillis()) { error = "Choose a time in the future."; return }
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            pendingReminder = item.id to time
            notifications.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) {
            error = "Enable Jarvis notifications in Android settings before adding a reminder."
        } else update { store.setReminder(item.id, time) }
    }
    LaunchedEffect(revision) {
        runCatching { withContext(Dispatchers.IO) { store.items() } }
            .onSuccess { entries = it }.onFailure { error = "Couldn't load your saved items." }
        loading = false
    }
    val selected = entries.firstOrNull { it.id == selectedId }
    val visible = entries.filter { item ->
        (kind == null || item.kind.name == kind) &&
            (query.isBlank() || "${item.title} ${item.summary} ${item.searchableText}".contains(query.trim(), true))
    }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        BackHandler(selected != null) { selectedId = null }
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(Modifier.safeDrawingPadding().imePadding().fillMaxSize().widthIn(max = 900.dp)) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = { if (selected != null) selectedId = null else onDismiss() }) {
                        Text(if (selected != null) "Back" else "Close")
                    }
                    Text(if (selected != null) "Saved item" else "Remember later", style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                error?.let { message ->
                    Surface(color = MaterialTheme.colorScheme.errorContainer, modifier = Modifier.fillMaxWidth().padding(16.dp), shape = RoundedCornerShape(12.dp)) {
                        Column(Modifier.padding(12.dp)) {
                            Text(message, color = MaterialTheme.colorScheme.onErrorContainer)
                            TextButton(onClick = { error = null; revision++ }) { Text("Dismiss") }
                        }
                    }
                }
                if (selected != null) {
                    InboxDetail(selected, onAskJarvis = { onAskJarvis(selected) },
                        onRemind = { reminderItem = selected }, onDelete = { deleteItem = selected })
                } else {
                    Text("Your notes, links, photos, and voice notes. Saved on this phone.",
                        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp))
                    OutlinedTextField(query, { query = it }, label = { Text("Search saved items") }, singleLine = true,
                        trailingIcon = { if (query.isNotEmpty()) TextButton(onClick = { query = "" }) { Text("Clear") } },
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp), shape = RoundedCornerShape(16.dp))
                    LazyRow(contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        item { FilterChip(selected = kind == null, onClick = { kind = null }, label = { Text("All") }) }
                        items(RememberKind.entries) { type ->
                            FilterChip(selected = kind == type.name, onClick = { kind = type.name }, label = { Text(type.label()) })
                        }
                    }
                    if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())
                    if (!loading && visible.isEmpty()) {
                        Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(32.dp),
                            verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(if (entries.isEmpty()) "Keep something worth coming back to" else "No matching items",
                                style = MaterialTheme.typography.titleMedium)
                            Spacer(Modifier.height(8.dp))
                            Text(if (entries.isEmpty()) "In another app, tap Share and choose Jarvis. Your saved item will appear here."
                                else "Try a different word or choose All to search every type.",
                                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    } else LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        items(visible, key = { it.id }) { item ->
                            OutlinedCard(onClick = { selectedId = item.id }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp)) {
                                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text(item.kind.label() + " · " + formatInboxTime(item.createdAt), style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text(item.title, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                    Text(item.summary, style = MaterialTheme.typography.bodyMedium, maxLines = 3, overflow = TextOverflow.Ellipsis)
                                    item.reminderAt?.let { Text("Reminder · " + formatInboxTime(it), style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.primary) }
                                    Text("Open item", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    deleteItem?.let { item ->
        AlertDialog(onDismissRequest = { deleteItem = null }, title = { Text("Delete saved item?") },
            text = { Text("This removes “${item.title}” and its reminder from Jarvis.") },
            confirmButton = { TextButton(onClick = {
                update { store.delete(item.id) }; selectedId = null; deleteItem = null
            }) { Text("Delete", color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton(onClick = { deleteItem = null }) { Text("Keep item") } })
    }
    reminderItem?.let { item ->
        AlertDialog(onDismissRequest = { reminderItem = null }, title = { Text("Remind me") },
            text = {
                Column {
                    Text("Android may deliver reminders a little later to save battery.", style = MaterialTheme.typography.bodySmall)
                    TextButton(onClick = { schedule(item, System.currentTimeMillis() + 3_600_000); reminderItem = null }) { Text("In one hour") }
                    TextButton(onClick = {
                        val time = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, 1); set(Calendar.HOUR_OF_DAY, 9); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0) }
                        schedule(item, time.timeInMillis); reminderItem = null
                    }) { Text("Tomorrow at 9:00 AM") }
                    TextButton(onClick = {
                        reminderItem = null
                        val now = Calendar.getInstance()
                        DatePickerDialog(context, { _, year, month, day ->
                            TimePickerDialog(context, { _, hour, minute ->
                                val chosen = Calendar.getInstance().apply { set(year, month, day, hour, minute, 0); set(Calendar.MILLISECOND, 0) }
                                schedule(item, chosen.timeInMillis)
                            }, now.get(Calendar.HOUR_OF_DAY), now.get(Calendar.MINUTE), android.text.format.DateFormat.is24HourFormat(context)).show()
                        }, now.get(Calendar.YEAR), now.get(Calendar.MONTH), now.get(Calendar.DAY_OF_MONTH)).apply {
                            datePicker.minDate = System.currentTimeMillis(); show()
                        }
                    }) { Text("Choose date and time") }
                    if (item.reminderAt != null) TextButton(onClick = { update { store.setReminder(item.id, null) }; reminderItem = null }) { Text("Remove reminder") }
                }
            }, confirmButton = { TextButton(onClick = { reminderItem = null }) { Text("Cancel") } })
    }
}

@Composable
private fun InboxDetail(item: RememberItem, onAskJarvis: () -> Unit, onRemind: () -> Unit, onDelete: () -> Unit) {
    val context = LocalContext.current
    var player by remember(item.id) { mutableStateOf<MediaPlayer?>(null) }
    var playing by remember(item.id) { mutableStateOf(false) }
    var preparing by remember(item.id) { mutableStateOf(false) }
    DisposableEffect(item.id) { onDispose { player?.release() } }
    val bitmap by produceState<android.graphics.Bitmap?>(null, item.mediaPath) {
        if (item.kind == RememberKind.PHOTO) value = withContext(Dispatchers.IO) { BitmapFactory.decodeFile(item.mediaPath) }
    }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(item.title, style = MaterialTheme.typography.headlineSmall)
        Text(item.kind.label() + " · " + formatInboxTime(item.createdAt), style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        bitmap?.let { Image(it.asImageBitmap(), "Saved photo", contentScale = ContentScale.Fit,
            modifier = Modifier.fillMaxWidth().heightIn(max = 420.dp)) }
        if (item.kind == RememberKind.VOICE) {
            Button(enabled = !preparing, onClick = {
                if (playing) { player?.pause(); playing = false }
                else if (player != null) { player?.start(); playing = true }
                else runCatching {
                    preparing = true
                    player = MediaPlayer().apply {
                        setDataSource(item.mediaPath)
                        setOnPreparedListener { preparing = false; it.start(); playing = true }
                        setOnCompletionListener { playing = false }
                        setOnErrorListener { mp, _, _ ->
                            mp.release(); player = null; preparing = false; playing = false
                            Toast.makeText(context, "Couldn't play this recording.", Toast.LENGTH_LONG).show(); true
                        }
                        prepareAsync()
                    }
                }.onFailure { player?.release(); player = null; preparing = false
                    Toast.makeText(context, "Couldn't play this recording.", Toast.LENGTH_LONG).show() }
            }) { Text(if (preparing) "Preparing audio…" else if (playing) "Pause voice note" else "Play voice note") }
            Text("Voice notes are searchable by filename. Audio transcription is not available yet.", style = MaterialTheme.typography.bodySmall)
        } else {
            SelectionContainer { Text(item.searchableText.ifBlank { item.summary }, style = MaterialTheme.typography.bodyLarge) }
            if (item.kind == RememberKind.LINK) OutlinedButton(onClick = {
                val uri = Uri.parse(item.searchableText)
                if (uri.scheme in listOf("http", "https")) runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, uri)) }
                    .onFailure { Toast.makeText(context, "No app is available to open this link.", Toast.LENGTH_LONG).show() }
            }) { Text("Open link") }
            if (item.searchableText.isNotBlank()) Button(onClick = onAskJarvis) { Text("Ask Jarvis about this") }
        }
        item.reminderAt?.let { Text("Reminder · " + formatInboxTime(it), color = MaterialTheme.colorScheme.primary) }
        OutlinedButton(onClick = onRemind) { Text(if (item.reminderAt == null) "Add reminder" else "Change reminder") }
        TextButton(onClick = onDelete) { Text("Delete item", color = MaterialTheme.colorScheme.error) }
    }
}

private fun RememberKind.label() = when (this) {
    RememberKind.TEXT -> "Notes"
    RememberKind.LINK -> "Links"
    RememberKind.PHOTO -> "Photos"
    RememberKind.VOICE -> "Voice notes"
}
private fun formatInboxTime(time: Long) = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(time))

fun RememberItem.toJarvisPrompt(): String = "Help me understand this saved item:\n$title\n${searchableText.ifBlank { summary }}".take(3800)
