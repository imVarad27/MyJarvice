package com.example.myjarvice.data

import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.io.File
import android.net.Uri
import org.json.JSONArray
import org.json.JSONObject
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** Test APK storage only. Do not execute instrumentation on a personal phone. */
class KnowledgeLibraryStoreTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().context
    private val store get() = LocalKnowledgeStore(context)
    private val file get() = File(context.filesDir, "local-knowledge.json")

    @Before @After fun cleanSyntheticStore() {
        file.delete()
        File(file.path + ".bak").delete()
        File(file.path + ".new").delete()
    }

    @Test fun editingAndExclusionSurviveReopening() {
        val saved = store.remember("Synthetic preference")
        store.setEnabled(saved.id, false)
        store.editMemory(saved.id, "Synthetic edited preference", saved.text)
        val reopened = store.entries().single()
        assertEquals(saved.id, reopened.id)
        assertFalse(reopened.enabled)
        assertTrue(store.search("edited preference").isEmpty())
        // Re-saving an excluded duplicate cannot silently re-enable it.
        assertFalse(store.remember(reopened.text).enabled)
        store.deleteReviewed(reopened)
        assertTrue(store.entries().isEmpty())
    }

    @Test fun unreadableStoreCannotBeOverwrittenByAnEditOrNewMemory() {
        file.writeText("invalid synthetic json")
        assertTrue(runCatching { store.remember("New synthetic memory") }.isFailure)
        assertTrue(runCatching { store.setEnabled("m", true) }.isFailure)
        assertEquals("invalid synthetic json", file.readText())
    }

    @Test fun excludedMemoriesStayOutOfActualToolObservations() {
        val entry = store.remember("Synthetic secret preference")
        store.setEnabled(entry.id, false)
        val result = LocalAgentTools(context).execute(LocalToolCall("list_memories", ""))
        assertFalse(result.hasData)
        assertFalse(result.text.contains(entry.text))
    }

    @Test fun versionTwoBackupRestoresExclusionButDoesNotOverrideCurrentChoice() {
        val archive = File(context.cacheDir, "synthetic-knowledge-v2.jarvisbackup")
        try {
            val entry = store.remember("Synthetic backup preference")
            store.setEnabled(entry.id, false)
            val manager = JarvisBackupManager(context)
            manager.exportTo(Uri.fromFile(archive))
            store.deleteReviewed(store.entries().single())
            manager.inspect(Uri.fromFile(archive))
            manager.restoreFrom(Uri.fromFile(archive))
            assertFalse(store.entries().single().enabled)
            store.setEnabled(entry.id, true)
            manager.restoreFrom(Uri.fromFile(archive))
            assertTrue(store.entries().single().enabled)
        } finally { archive.delete() }
    }

    @Test fun actualVersionOneArchiveStillRestoresOldEnabledBehavior() {
        val archive = File(context.cacheDir, "synthetic-knowledge-v1.jarvisbackup")
        try {
            val root = JSONObject().put("format", "jarvis-local-backup").put("version", 1).put("createdAt", 123)
                .put("conversations", JSONArray()).put("savedItems", JSONArray()).put("tasks", JSONArray())
                .put("knowledge", JSONArray().put(JSONObject().put("id", "legacy-fixture").put("name", "Saved memory")
                    .put("text", "Synthetic legacy preference").put("memory", true)))
            ZipOutputStream(archive.outputStream()).use { zip ->
                zip.putNextEntry(ZipEntry("manifest.json"))
                zip.write(root.toString().toByteArray(Charsets.UTF_8))
                zip.closeEntry()
            }
            val manager = JarvisBackupManager(context)
            assertEquals(1, manager.inspect(Uri.fromFile(archive)).memories)
            manager.restoreFrom(Uri.fromFile(archive))
            assertTrue(store.entries().single().enabled)
        } finally { archive.delete() }
    }
}
