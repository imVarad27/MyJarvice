package com.example.myjarvice.ui.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.myjarvice.data.*
import com.example.myjarvice.ui.JarvisIconBadge
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun KnowledgeLibraryDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val store = remember { LocalKnowledgeStore(context.applicationContext) }
    val scope = rememberCoroutineScope()
    var entries by remember { mutableStateOf(emptyList<KnowledgeEntry>()) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var status by remember { mutableStateOf<String?>(null) }
    var readable by remember { mutableStateOf(false) }

    fun perform(message: String? = null, done: () -> Unit = {}, operation: () -> Unit = {}) {
        if (busy) return
        busy = true
        error = null
        status = null
        scope.launch {
            val result = withContext(Dispatchers.IO) { runCatching { operation(); store.entries() } }
            result.onSuccess { entries = it; readable = true; status = message; done() }
                .onFailure { error = KnowledgeLibrary.failureMessage(it) }
            busy = false
        }
    }
    LaunchedEffect(store) { perform() }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_RESUME) perform() }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) perform("Document imported on this phone.") { store.importDocument(uri) }
    }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        KnowledgeLibraryScreen(entries, busy, readable, error, status, onDismiss,
            onRefresh = { perform() },
            onImport = { picker.launch(arrayOf("*/*")) },
            onSave = { entry, text, done ->
                perform("Memory saved on this phone.", done) {
                    if (entry == null) store.remember(text) else store.editMemory(entry.id, text, entry.text)
                }
            },
            onToggle = { entry, enabled -> perform(if (enabled) "Available for future phone-model retrieval." else "Excluded from future phone-model retrieval.") {
                store.setEnabled(entry.id, enabled)
            } },
            onDelete = { entry, done -> perform("Removed from Jarvis. Original files and past chats are unchanged.", done) { store.deleteReviewed(entry) } })
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun KnowledgeLibraryScreen(
    entries: List<KnowledgeEntry>, busy: Boolean, readable: Boolean, error: String?, status: String?,
    onDismiss: () -> Unit, onRefresh: () -> Unit, onImport: () -> Unit,
    onSave: (KnowledgeEntry?, String, () -> Unit) -> Unit,
    onToggle: (KnowledgeEntry, Boolean) -> Unit,
    onDelete: (KnowledgeEntry, () -> Unit) -> Unit
) {
    var query by rememberSaveable { mutableStateOf("") }
    var filter by rememberSaveable { mutableStateOf(KnowledgeFilter.ALL) }
    var selectedId by rememberSaveable { mutableStateOf<String?>(null) }
    var editorOpen by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<KnowledgeEntry?>(null) }
    var pendingDelete by remember { mutableStateOf<KnowledgeEntry?>(null) }
    val visible = remember(entries, query, filter) { KnowledgeLibrary.visible(entries, query, filter) }
    val selected = entries.firstOrNull { it.id == selectedId }
    val colors = MaterialTheme.colorScheme
    Scaffold(containerColor = colors.background, topBar = {
        TopAppBar(title = { Text("Library", maxLines = 1, overflow = TextOverflow.Ellipsis) }, navigationIcon = {
            IconButton(onClick = onDismiss) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Close library") }
        }, actions = {
            IconButton(onClick = onRefresh, enabled = !busy) { Icon(Icons.Rounded.Refresh, "Refresh library") }
            IconButton(onClick = onImport, enabled = readable && !busy && entries.count { !it.memory } < 20) {
                Icon(Icons.Rounded.UploadFile, "Import document")
            }
        })
    }, floatingActionButton = {
        if (readable && !busy && entries.count { it.memory } < 50) ExtendedFloatingActionButton(
            onClick = { editing = null; editorOpen = true }, icon = { Icon(Icons.Rounded.Add, null) }, text = { Text("Add memory") })
    }) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding).imePadding().padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(bottom = 104.dp)) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("You decide what Jarvis remembers.", style = MaterialTheme.typography.titleMedium)
                    Text("Private to this phone. Enabled items can be retrieved by phone-model tools, not automatically included in every reply or sent to your PC.",
                        style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
                    OutlinedTextField(query, { query = it.take(200) }, Modifier.fillMaxWidth(), singleLine = true,
                        label = { Text("Search library") }, leadingIcon = { Icon(Icons.Rounded.Search, null) },
                        trailingIcon = { if (query.isNotEmpty()) IconButton(onClick = { query = "" }) { Icon(Icons.Rounded.Close, "Clear library search") } })
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        KnowledgeFilter.entries.forEach { option ->
                            FilterChip(filter == option, onClick = { filter = option }, label = { Text(option.title) })
                        }
                    }
                    Text("${entries.count { it.memory }}/50 memories · ${entries.count { !it.memory }}/20 documents",
                        style = MaterialTheme.typography.labelMedium, color = colors.onSurfaceVariant)
                    if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
                    error?.let { Text(it, color = colors.error, style = MaterialTheme.typography.bodyMedium) }
                    status?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
                }
            }
            if (readable && visible.isEmpty()) item {
                Surface(color = colors.surfaceContainerLow, shape = MaterialTheme.shapes.large) {
                    Column(Modifier.fillMaxWidth().padding(24.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        JarvisIconBadge(Icons.Rounded.AutoStories)
                        Text(if (entries.isEmpty()) "A little context goes a long way" else "No matching items", style = MaterialTheme.typography.titleMedium)
                        Text(if (entries.isEmpty()) "Save a preference or import notes. Only explicit memories are saved—Jarvis doesn't infer private facts."
                            else "Try another search or choose All.", style = MaterialTheme.typography.bodyMedium)
                        Text("Documents: PDF, TXT or Markdown · up to 5 MB and 50 pages. Scanned PDFs need OCR and aren't supported here.",
                            style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
                    }
                }
            }
            items(visible, key = { it.id }) { entry ->
                Surface(Modifier.fillMaxWidth().clickable { selectedId = entry.id }, shape = MaterialTheme.shapes.large, color = colors.surfaceContainerLow) {
                    Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        JarvisIconBadge(if (entry.memory) Icons.Rounded.Memory else Icons.Rounded.Description)
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(if (entry.memory) entry.text else entry.name, style = MaterialTheme.typography.titleSmall, maxLines = 3, overflow = TextOverflow.Ellipsis)
                            Text("${if (entry.memory) "Memory" else "Document"} · ${if (entry.enabled) "Available to phone model" else "Excluded from phone model"}",
                                style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
                        }
                        Icon(Icons.Rounded.ChevronRight, "Open item")
                    }
                }
            }
        }
    }
    selected?.let { entry ->
        Dialog(onDismissRequest = { selectedId = null }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
            val chunks = remember(entry.text) { entry.text.chunked(2000) }
            Scaffold(topBar = {
                TopAppBar(title = { Text(if (entry.memory) "Saved memory" else "Document", maxLines = 1) }, navigationIcon = {
                    IconButton(onClick = { selectedId = null }) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Close item") }
                }, actions = {
                    if (entry.memory) IconButton(onClick = { editing = entry; editorOpen = true }, enabled = !busy) { Icon(Icons.Rounded.Edit, "Edit memory") }
                    IconButton(onClick = { pendingDelete = entry }, enabled = !busy) { Icon(Icons.Rounded.DeleteOutline, "Remove saved item") }
                })
            }) { padding ->
                LazyColumn(Modifier.fillMaxSize().padding(padding).padding(horizontal = 20.dp), contentPadding = PaddingValues(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    item {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (!entry.memory) Text(entry.name, style = MaterialTheme.typography.titleLarge)
                            Text("${entry.text.length} characters · stored on this phone", style = MaterialTheme.typography.labelMedium)
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                Column(Modifier.weight(1f)) {
                                    Text("Use in phone-model tools", style = MaterialTheme.typography.titleSmall)
                                    Text("Turning off keeps the item but excludes it from future library search and memory listing. It doesn't erase past chats or results already in progress.", style = MaterialTheme.typography.bodySmall)
                                }
                                Switch(entry.enabled, onCheckedChange = { onToggle(entry, it) }, enabled = !busy,
                                    modifier = Modifier.semantics { contentDescription = "Use this item in phone-model tools" })
                            }
                            error?.let { Text(it, color = colors.error) }
                            status?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                            HorizontalDivider()
                        }
                    }
                    items(chunks) { chunk ->
                        SelectionContainer { Text(chunk, style = MaterialTheme.typography.bodyLarge) }
                    }
                }
            }
        }
    }
    if (editorOpen) MemoryEditor(editing, entries, busy, error, onDismiss = { if (!busy) editorOpen = false },
        onSave = { text -> onSave(editing, text) { editorOpen = false } })
    pendingDelete?.let { entry ->
        AlertDialog(onDismissRequest = { if (!busy) pendingDelete = null }, title = { Text("Remove saved ${if (entry.memory) "memory" else "document"}?") },
            text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(if (entry.memory) entry.text else entry.name, maxLines = 4, overflow = TextOverflow.Ellipsis)
                Text("Removes this item from Jarvis. Original files, past chats and existing backup copies are unchanged.")
                error?.let { Text(it, color = colors.error) }
            } },
            confirmButton = { TextButton(enabled = !busy, onClick = { onDelete(entry) { pendingDelete = null; selectedId = null } }) { Text("Remove", color = colors.error) } },
            dismissButton = { TextButton(enabled = !busy, onClick = { pendingDelete = null }) { Text("Cancel") } })
    }
}

@Composable
private fun MemoryEditor(entry: KnowledgeEntry?, entries: List<KnowledgeEntry>, busy: Boolean, error: String?, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var text by rememberSaveable(entry?.id) { mutableStateOf(entry?.text.orEmpty()) }
    val duplicate = entries.any { it.id != entry?.id && it.memory && it.text == text.trim() }
    AlertDialog(onDismissRequest = onDismiss, title = { Text(if (entry == null) "Add memory" else "Edit memory") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()).imePadding(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Save only what you want Jarvis to know. Editing changes future retrieval, not old chats.", style = MaterialTheme.typography.bodySmall)
                OutlinedTextField(text, { text = it.take(300) }, Modifier.fillMaxWidth(), enabled = !busy, minLines = 3,
                    label = { Text("Memory text") }, isError = duplicate,
                    supportingText = { Text(if (duplicate) "This fact is already saved." else "${text.length}/300") })
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        }, confirmButton = { TextButton(enabled = !busy && !duplicate && text.isNotBlank() && '\u0000' !in text, onClick = { onSave(text) }) { Text("Save memory") } },
        dismissButton = { TextButton(enabled = !busy, onClick = onDismiss) { Text("Cancel") } })
}
