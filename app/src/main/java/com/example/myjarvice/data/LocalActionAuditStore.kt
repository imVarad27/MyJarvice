package com.example.myjarvice.data

import android.content.Context
import android.util.AtomicFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import java.io.File

/** App-private, bounded metadata log. Never uploaded or included in backups. */
class LocalActionAuditStore(context: Context) {
    private val file = AtomicFile(File(context.noBackupFilesDir, "phone-activity.json"))
    private val journal = PhoneActivityJournal(read = {
        if (!file.baseFile.exists() && !File(file.baseFile.path + ".bak").exists()) null
        else file.openRead().use { input ->
            val bytes = PhoneActivityJournal.readBounded(input)
            bytes.toString(Charsets.UTF_8)
        }
    }, write = { text ->
        val output = file.startWrite()
        try { output.write(text.toByteArray(Charsets.UTF_8)); file.finishWrite(output) }
        catch (error: Exception) { file.failWrite(output); throw error }
    })

    fun recent(): Result<List<ActionAuditEvent>> = runCatching { journal.recent() }
    fun observe() = revision.map { withContext(Dispatchers.IO) { recent() } }
    fun record(type: String, outcome: String) {
        // A logging failure must never turn a completed action into a failed action.
        if (runCatching { journal.record(type, outcome) }.isSuccess) revision.update { it + 1 }
    }
    fun clear(): Result<Unit> = runCatching { journal.clear(); revision.update { it + 1 } }
    companion object { private val revision = MutableStateFlow(0L) }
}
