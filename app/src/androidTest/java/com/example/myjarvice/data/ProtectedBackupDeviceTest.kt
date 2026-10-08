package com.example.myjarvice.data

import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import android.net.Uri
import android.os.Build
import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import java.util.UUID
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import java.util.zip.ZipEntry
import org.json.JSONObject

/** Isolated synthetic stores; compile-only on the personal phone, execute only on a test emulator. */
class ProtectedBackupDeviceTest {
    private val base = ApplicationProvider.getApplicationContext<Context>()
    private val roots = mutableListOf<File>()
    private val preferenceNames = mutableSetOf<String>()
    private val password = "four unrelated words here".toCharArray()

    private fun isolated(): Context {
        val name = "backup-test-${UUID.randomUUID()}"
        val root = File(base.cacheDir, name).apply { check(mkdirs()) }
        roots += root
        return object : ContextWrapper(base) {
            override fun getFilesDir(): File = root
            override fun getSharedPreferences(key: String, mode: Int): SharedPreferences {
                val isolatedName = "$name-$key"
                preferenceNames += isolatedName
                return base.getSharedPreferences(isolatedName, mode)
            }
        }
    }

    @After fun cleanup() {
        password.fill('\u0000')
        preferenceNames.forEach { base.getSharedPreferences(it, Context.MODE_PRIVATE).edit().clear().commit() }
        roots.forEach { root -> check(root.parentFile?.canonicalFile == base.cacheDir.canonicalFile); root.deleteRecursively() }
    }

    @Test fun authenticatedReviewIsReadOnlyAndMergeUsesThatSnapshot() {
        assumeTrue(Build.VERSION.SDK_INT >= 26)
        val source = isolated()
        val target = isolated()
        val sourceKnowledge = LocalKnowledgeStore(source)
        val memory = sourceKnowledge.remember("Synthetic preference: quiet mornings")
        sourceKnowledge.setEnabled(memory.id, false)
        val media = byteArrayOf(1, 2, 3, 4)
        RememberInboxStore(source).addVoice("Synthetic voice note", media)
        LocalTaskStore(source).saveTask(title = "Synthetic source task")
        ChatHistoryStore(source).saveSession(ChatSession("synthetic-chat", "Synthetic", 1, 2,
            listOf(JarvisMessage("USER", "Synthetic message", "MESSAGE", "now"))))
        SettingsStore(source).apply { userName = "Source"; serverToken = "NEVER_EXPORT_THIS_TOKEN" }
        SettingsStore(target).apply { userName = "Target"; serverToken = "RETAIN_TARGET_TOKEN" }
        LocalTaskStore(target).saveTask(title = "Keep existing task")
        val file = File(source.filesDir, "protected.jarvisbackup")
        val uri = Uri.fromFile(file)
        val exporter = JarvisBackupManager(source)
        exporter.exportTo(uri, password)
        val decrypted = file.inputStream().use { BackupProtection.open(it, password) }
        try {
            val metadata = ZipInputStream(decrypted.inputStream()).use { zip ->
                assertEquals("manifest.json", zip.nextEntry.name)
                zip.readBytes().toString(Charsets.UTF_8)
            }
            assertFalse(metadata.contains("NEVER_EXPORT_THIS_TOKEN"))
            assertFalse(metadata.contains("serverToken"))
        } finally { decrypted.fill(0) }
        val manager = JarvisBackupManager(target)
        assertTrue(manager.requiresPassphrase(uri))
        try { manager.prepareRestore(uri, "incorrect passphrase here".toCharArray()); fail("Accepted wrong password") }
        catch (_: IllegalArgumentException) { }
        assertEquals("Target", SettingsStore(target).userName)
        assertEquals(1, LocalTaskStore(target).tasks().size)
        assertTrue(LocalKnowledgeStore(target).entries().isEmpty())
        val snapshot = manager.prepareRestore(uri, password)
        assertEquals(1, snapshot.preview.memories)
        assertEquals("Target", SettingsStore(target).userName)
        // Change the external document after review. Only the original snapshot is approved.
        sourceKnowledge.remember("Unexpected replacement memory")
        SettingsStore(source).userName = "Replacement"
        exporter.exportTo(uri, password)
        manager.restorePrepared(snapshot)
        assertEquals("Source", SettingsStore(target).userName)
        assertEquals("RETAIN_TARGET_TOKEN", SettingsStore(target).serverToken)
        assertEquals(2, LocalTaskStore(target).tasks().size)
        assertEquals(1, LocalKnowledgeStore(target).entries().size)
        assertFalse(LocalKnowledgeStore(target).entries().single().enabled)
        val restoredMedia = RememberInboxStore(target).items().single().mediaPath!!
        assertArrayEquals(media, File(restoredMedia).readBytes())
        assertEquals(1, ChatHistoryStore(target).loadAllSessions().size)
        try { manager.restorePrepared(snapshot); fail("Snapshot reused") }
        catch (_: IllegalStateException) { }
    }

    @Test fun oldUnencryptedVersionOneBackupsStillMerge() {
        val source = isolated()
        val target = isolated()
        LocalTaskStore(source).saveTask(title = "Legacy task")
        val file = File(source.filesDir, "legacy.jarvisbackup")
        JarvisBackupManager(source).exportTo(Uri.fromFile(file))
        val manifest = ZipInputStream(file.inputStream()).use { zip ->
            assertEquals("manifest.json", zip.nextEntry.name)
            JSONObject(zip.readBytes().toString(Charsets.UTF_8)).put("version", 1)
        }
        ZipOutputStream(file.outputStream()).use { zip ->
            zip.putNextEntry(ZipEntry("manifest.json")); zip.write(manifest.toString().toByteArray()); zip.closeEntry()
        }
        val manager = JarvisBackupManager(target)
        assertFalse(manager.requiresPassphrase(Uri.fromFile(file)))
        val snapshot = manager.prepareRestore(Uri.fromFile(file))
        assertEquals(1, snapshot.preview.tasks)
        manager.restorePrepared(snapshot)
        assertEquals("Legacy task", LocalTaskStore(target).tasks().single().title)
    }
}
