package com.example.myjarvice

import android.content.Intent
import android.database.Cursor
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import com.example.myjarvice.data.ImageUnderstanding
import com.example.myjarvice.data.RememberInboxStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Receives Android Share-sheet content, stores it privately, then opens Jarvis. */
class ShareReceiverActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        lifecycleScope.launch {
            val saved = withContext(Dispatchers.IO) { saveSharedContent(intent) }
            Toast.makeText(this@ShareReceiverActivity, if (saved) "Saved to Jarvis · Remember later" else "Jarvis could not save that item", Toast.LENGTH_SHORT).show()
            startActivity(Intent(this@ShareReceiverActivity, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP))
            finish()
        }
    }

    private suspend fun saveSharedContent(shared: Intent): Boolean {
        val store = RememberInboxStore(applicationContext)
        val text = shared.getCharSequenceExtra(Intent.EXTRA_TEXT)?.toString()?.trim()
        if (!text.isNullOrBlank()) {
            store.addText(text)
            return true
        }
        @Suppress("DEPRECATION")
        val sharedUris = shared.getParcelableArrayListExtra<Uri>(Intent.EXTRA_STREAM).orEmpty()
        if (sharedUris.isNotEmpty()) {
            var savedAny = false
            for (uri in sharedUris) if (saveUri(store, uri)) savedAny = true
            return savedAny
        }
        val uri = shared.getParcelableExtra<Uri>(Intent.EXTRA_STREAM) ?: shared.clipData?.getItemAt(0)?.uri ?: return false
        return saveUri(store, uri)
    }

    private suspend fun saveUri(store: RememberInboxStore, uri: Uri): Boolean {
        val mime = contentResolver.getType(uri).orEmpty()
        return when {
            mime.startsWith("image/") -> ImageUnderstanding.prepare(applicationContext, uri).map { store.addPhoto(it) }.isSuccess
            mime.startsWith("audio/") -> {
                val bytes = contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: return false
                if (bytes.size > 30 * 1024 * 1024) return false
                val ext = mime.substringAfter('/', "m4a").substringBefore('+').ifBlank { "m4a" }
                store.addVoice(displayName(uri), bytes, ext)
                true
            }
            else -> false
        }
    }

    private fun displayName(uri: Uri): String {
        contentResolver.query(uri, null, null, null, null)?.use { cursor: Cursor ->
            val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (cursor.moveToFirst() && index >= 0) return cursor.getString(index)
        }
        return "Voice note"
    }
}
