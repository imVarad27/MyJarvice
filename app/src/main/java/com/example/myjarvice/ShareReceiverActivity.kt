package com.example.myjarvice

import android.app.Application
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewModelScope
import com.example.myjarvice.data.ImageUnderstanding
import com.example.myjarvice.data.RememberInboxStore
import com.example.myjarvice.data.SettingsStore
import com.example.myjarvice.theme.MyJarvisTheme
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.ByteArrayOutputStream

/** Retains import state across rotation; shows progress and recoverable errors. */
class ShareReceiverActivity : ComponentActivity() {
    private val importer: ShareImportViewModel by viewModels()
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        importer.startImport(intent)
        setContent {
            val state by importer.state.collectAsState()
            val settings = remember { SettingsStore(applicationContext) }
            MyJarvisTheme(themeMode = settings.themeMode, dynamicColor = settings.dynamicColor) {
                Surface(Modifier.fillMaxSize()) {
                    Column(Modifier.safeDrawingPadding().fillMaxSize().padding(32.dp),
                        verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(if (!state.done) "Saving to Jarvis…" else if (state.saved > 0) "Saved for later" else "Couldn't save these items", style = MaterialTheme.typography.headlineSmall)
                        Spacer(Modifier.height(16.dp))
                        if (!state.done) CircularProgressIndicator()
                        else {
                            Text("${state.saved} item(s) saved on this phone.", style = MaterialTheme.typography.bodyLarge)
                            if (state.failed > 0) Text("${state.failed} item(s) couldn't be saved. Try sharing a smaller file or a different format.",
                                modifier = Modifier.padding(vertical = 16.dp), color = MaterialTheme.colorScheme.error)
                            Button(onClick = { openInbox() }) { Text("Open inbox") }
                            TextButton(onClick = { finish() }) { Text("Done") }
                        }
                    }
                }
            }
        }
        lifecycleScope.launch {
            importer.state.collect { if (it.done && it.failed == 0) openInbox() }
        }
    }
    private fun openInbox() {
        startActivity(Intent(this, MainActivity::class.java)
            .putExtra(MainActivity.EXTRA_OPEN_INBOX, true).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP))
        finish()
    }
}

data class ShareImportState(val done: Boolean = false, val saved: Int = 0, val failed: Int = 0)

class ShareImportViewModel(application: Application) : AndroidViewModel(application) {
    private val mutableState = MutableStateFlow(ShareImportState())
    val state = mutableState.asStateFlow()
    private var started = false

    @Suppress("DEPRECATION")
    fun startImport(shared: Intent) {
        if (started) return
        started = true
        viewModelScope.launch(Dispatchers.IO) {
            val context = getApplication<Application>()
            val resolver = context.contentResolver
            val store = RememberInboxStore(context)
            var saved = 0
            var failed = 0
            val text = shared.getCharSequenceExtra(Intent.EXTRA_TEXT)?.toString()?.trim().orEmpty()
            if (text.isNotEmpty()) runCatching { store.addText(text.take(100_000)) }
                .onSuccess { saved++ }.onFailure { failed++ }
            val uris = buildList {
                if (shared.action == Intent.ACTION_SEND_MULTIPLE) addAll(shared.getParcelableArrayListExtra<Uri>(Intent.EXTRA_STREAM).orEmpty())
                else shared.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)?.let { add(it) }
                shared.clipData?.let { clip -> for (i in 0 until clip.itemCount) clip.getItemAt(i).uri?.let { add(it) } }
            }.distinct()
            for (uri in uris.take(20)) {
                try {
                    val mime = resolver.getType(uri) ?: shared.type.orEmpty()
                    when {
                        mime.startsWith("image/") -> store.addPhoto(ImageUnderstanding.prepare(context, uri).getOrThrow())
                        mime.startsWith("audio/") -> {
                            val bytes = resolver.openInputStream(uri)?.use { input ->
                                val out = ByteArrayOutputStream()
                                val buffer = ByteArray(8192)
                                var total = 0
                                while (true) {
                                    val count = input.read(buffer)
                                    if (count < 0) break
                                    total += count
                                    require(total <= 30 * 1024 * 1024) { "Audio file is too large" }
                                    out.write(buffer, 0, count)
                                }
                                out.toByteArray()
                            } ?: error("File is unavailable")
                            val name = resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
                                if (it.moveToFirst()) it.getString(0) else null
                            } ?: "Voice note"
                            store.addVoice(name, bytes, "audio")
                        }
                        else -> error("Unsupported attachment")
                    }
                    saved++
                } catch (cancelled: CancellationException) { throw cancelled }
                catch (_: Exception) { failed++ }
            }
            failed += (uris.size - 20).coerceAtLeast(0)
            if (saved == 0 && failed == 0) failed = 1
            mutableState.value = ShareImportState(true, saved, failed)
        }
    }
}
