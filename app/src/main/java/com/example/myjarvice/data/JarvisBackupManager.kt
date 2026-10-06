package com.example.myjarvice.data

import android.content.Context
import android.net.Uri
import com.example.myjarvice.theme.AssistantStyle
import com.example.myjarvice.theme.ThemeMode
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

data class BackupPreview(
    val createdAt: Long,
    val conversations: Int,
    val memories: Int,
    val documents: Int,
    val savedItems: Int,
    val mediaFiles: Int,
    val tasks: Int = 0
) {
    val totalItems: Int get() = conversations + memories + documents + savedItems + tasks
}

internal object JarvisBackupPolicy {
    const val CURRENT_VERSION = 2
    fun isSupportedVersion(version: Int): Boolean = version in 1..CURRENT_VERSION
    private val mediaEntry = Regex("media/[A-Za-z0-9._-]{1,220}")
    fun isSafeMediaEntry(name: String): Boolean = ".." !in name && mediaEntry.matches(name)
}

/**
 * Local, user-initiated backup. Credentials, voiceprints, model binaries/paths and wake state are
 * intentionally excluded. Restore is merge-only so a backup cannot silently erase newer data.
 */
class JarvisBackupManager(private val context: Context) {
    private val chats = ChatHistoryStore(context)
    private val knowledge = LocalKnowledgeStore(context)
    private val inbox = RememberInboxStore(context)
    private val tasks = LocalTaskStore(context)
    private val settings = SettingsStore(context)
    private val writing = WritingProfileStore(context)

    fun exportTo(uri: Uri): BackupPreview {
        val sessions = chats.loadAllSessions()
        val entries = knowledge.entries()
        val saved = inbox.items()
        val mediaEntries = linkedMapOf<String, Pair<String, File>>()
        val mediaRoot = File(context.filesDir, "remember-inbox").canonicalFile
        saved.forEach { item ->
            item.mediaPath?.let(::File)?.takeIf { it.isFile }?.let { file ->
                val canonical = file.canonicalFile
                if (canonical.path.startsWith(mediaRoot.path + File.separator)) {
                    require(canonical.length() <= MAX_MEDIA_BYTES) { "A saved media item is larger than 12 MB." }
                    val extension = canonical.extension.lowercase().filter { it.isLetterOrDigit() }.take(8).ifBlank { "bin" }
                    mediaEntries[item.id] = "media/${item.id}.$extension" to canonical
                }
            }
        }
        val createdAt = System.currentTimeMillis()
        val manifest = JSONObject()
            .put("format", FORMAT)
            .put("version", JarvisBackupPolicy.CURRENT_VERSION)
            .put("createdAt", createdAt)
            .put("conversations", encodeSessions(sessions))
            .put("knowledge", encodeKnowledge(entries))
            .put("savedItems", encodeSavedItems(saved, mediaEntries.mapValues { it.value.first }))
            .put("tasks", encodeTasks(tasks.tasks()))
            .put("settings", encodeSettings())
            .put("writingProfile", encodeWritingProfile(writing.load()))
        val manifestBytes = manifest.toString().toByteArray(Charsets.UTF_8)
        require(manifestBytes.size <= MAX_MANIFEST_BYTES) { "Backup metadata is too large. Remove large chat images and try again." }
        val total = manifestBytes.size.toLong() + mediaEntries.values.sumOf { it.second.length() }
        require(total <= MAX_ARCHIVE_CONTENT_BYTES) { "Backup is larger than 32 MB. Remove some saved media and try again." }

        val output = context.contentResolver.openOutputStream(uri, "wt") ?: error("Cannot create this backup file.")
        ZipOutputStream(output.buffered()).use { zip ->
            zip.putNextEntry(ZipEntry(MANIFEST))
            zip.write(manifestBytes)
            zip.closeEntry()
            mediaEntries.values.forEach { (name, file) ->
                zip.putNextEntry(ZipEntry(name))
                file.inputStream().buffered().use { it.copyTo(zip) }
                zip.closeEntry()
            }
        }
        return preview(manifest, mediaEntries.size)
    }

    fun inspect(uri: Uri): BackupPreview = readArchive(uri).let { archive ->
        decodeSessions(archive.manifest.getJSONArray("conversations"))
        decodeKnowledge(archive.manifest.getJSONArray("knowledge"))
        decodeSavedItems(archive.manifest.getJSONArray("savedItems"), archive.media)
        decodeTasks(archive.manifest.optJSONArray("tasks") ?: JSONArray())
        preview(archive.manifest, archive.media.size)
    }

    fun restoreFrom(uri: Uri): BackupPreview {
        val archive = readArchive(uri)
        val root = archive.manifest
        val restoredChats = decodeSessions(root.getJSONArray("conversations"))
        val restoredKnowledge = decodeKnowledge(root.getJSONArray("knowledge"))
        val restoredSaved = decodeSavedItems(root.getJSONArray("savedItems"), archive.media)
        val restoredTasks = decodeTasks(root.optJSONArray("tasks") ?: JSONArray())

        chats.mergeSessions(restoredChats)
        knowledge.mergeEntries(restoredKnowledge)
        inbox.mergeItems(restoredSaved.first, restoredSaved.second)
        tasks.mergeTasks(restoredTasks)
        applySettings(root.optJSONObject("settings"))
        root.optJSONObject("writingProfile")?.let { writing.save(decodeWritingProfile(it)) }
        return preview(root, archive.media.size)
    }

    private data class Archive(val manifest: JSONObject, val media: Map<String, ByteArray>)

    private fun readArchive(uri: Uri): Archive {
        val source = context.contentResolver.openInputStream(uri) ?: error("Cannot read this backup file.")
        var manifest: JSONObject? = null
        val files = linkedMapOf<String, ByteArray>()
        var total = 0L
        var count = 0
        val seenNames = mutableSetOf<String>()
        ZipInputStream(source.buffered()).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                if (entry.isDirectory) continue
                count++
                require(count <= MAX_ZIP_ENTRIES) { "Backup contains too many files." }
                require(seenNames.add(entry.name)) { "Backup contains a duplicate file entry." }
                val limit = if (entry.name == MANIFEST) MAX_MANIFEST_BYTES else MAX_MEDIA_BYTES.toInt()
                val bytes = zip.readBounded(limit)
                total += bytes.size
                require(total <= MAX_ARCHIVE_CONTENT_BYTES) { "Backup expands beyond the 32 MB safety limit." }
                when {
                    entry.name == MANIFEST -> manifest = JSONObject(bytes.toString(Charsets.UTF_8))
                    JarvisBackupPolicy.isSafeMediaEntry(entry.name) -> files[entry.name] = bytes
                }
                zip.closeEntry()
            }
        }
        val root = manifest ?: error("This is not a Jarvis backup: manifest is missing.")
        require(root.optString("format") == FORMAT && JarvisBackupPolicy.isSupportedVersion(root.optInt("version"))) {
            "This backup version is not supported."
        }
        root.getJSONArray("conversations")
        root.getJSONArray("knowledge")
        root.getJSONArray("savedItems")
        return Archive(root, files)
    }

    private fun encodeSessions(sessions: List<ChatSession>) = JSONArray().also { array ->
        sessions.take(500).forEach { session ->
            array.put(JSONObject().put("id", session.id).put("title", session.title)
                .put("createdAt", session.createdAt).put("updatedAt", session.updatedAt)
                .put("messages", JSONArray().also { messages ->
                    session.messages.take(2_000).forEach { message ->
                        messages.put(JSONObject().put("sender", message.sender).put("text", message.text)
                            .put("type", message.type).put("timestamp", message.timestamp)
                            .apply { message.image?.let { put("image", it) } }
                            .put("sources", JSONArray().also { sources -> message.sources.take(30).forEach { source ->
                                sources.put(JSONObject().put("title", source.title).put("url", source.url).put("domain", source.domain))
                            } }))
                    }
                }))
        }
    }

    private fun decodeSessions(array: JSONArray): List<ChatSession> {
        require(array.length() <= 500) { "Backup contains too many conversations." }
        return List(array.length()) { index ->
            val obj = array.getJSONObject(index)
            val messages = obj.getJSONArray("messages")
            require(messages.length() <= 2_000) { "A conversation contains too many messages." }
            ChatSession(
                id = obj.getString("id").take(160),
                title = obj.optString("title", "Conversation").take(240),
                createdAt = obj.optLong("createdAt"),
                updatedAt = obj.optLong("updatedAt"),
                messages = List(messages.length()) { messageIndex ->
                    val message = messages.getJSONObject(messageIndex)
                    val text = message.optString("text")
                    require(text.length <= 100_000) { "A message is too large." }
                    JarvisMessage(
                        sender = message.optString("sender", "JARVIS").take(120),
                        text = text,
                        type = message.optString("type", "RESPONSE").take(40),
                        timestamp = message.optString("timestamp").take(80),
                        image = message.optString("image").ifBlank { null },
                        sources = message.optJSONArray("sources")?.let { sources -> List(minOf(sources.length(), 30)) { sourceIndex ->
                            val source = sources.getJSONObject(sourceIndex)
                            WebSource(source.optString("title").take(500), source.optString("url").take(2_000), source.optString("domain").take(200))
                        } } ?: emptyList()
                    )
                }
            )
        }
    }

    private fun encodeKnowledge(entries: List<KnowledgeEntry>) = JSONArray(KnowledgeLibrary.encode(entries))

    private fun decodeKnowledge(array: JSONArray): List<KnowledgeEntry> {
        return KnowledgeLibrary.decode(array.toString())
    }

    private fun encodeSavedItems(items: List<RememberItem>, mediaNames: Map<String, String>) = JSONArray().also { array ->
        items.take(1_000).forEach { item -> array.put(JSONObject().put("id", item.id).put("kind", item.kind.name)
            .put("title", item.title).put("summary", item.summary).put("searchableText", item.searchableText)
            .put("createdAt", item.createdAt).put("reminderAt", item.reminderAt ?: -1)
            .apply { mediaNames[item.id]?.let { put("mediaEntry", it) } }) }
    }

    private fun decodeSavedItems(array: JSONArray, mediaFiles: Map<String, ByteArray>): Pair<List<RememberItem>, Map<String, Pair<String, ByteArray>>> {
        require(array.length() <= 1_000) { "Backup contains too many saved items." }
        val media = linkedMapOf<String, Pair<String, ByteArray>>()
        val items = List(array.length()) { index ->
            val obj = array.getJSONObject(index)
            val id = obj.getString("id").take(160)
            val mediaName = obj.optString("mediaEntry")
            if (mediaName.isNotBlank()) mediaFiles[mediaName]?.let { bytes -> media[id] = mediaName.substringAfterLast('.', "bin") to bytes }
            val kind = runCatching { RememberKind.valueOf(obj.getString("kind")) }.getOrDefault(RememberKind.TEXT)
            if (kind == RememberKind.PHOTO || kind == RememberKind.VOICE) {
                require(id in media) { "A saved media item is missing from the backup." }
            }
            RememberItem(
                id = id,
                kind = kind,
                title = obj.optString("title").take(240),
                summary = obj.optString("summary").take(2_000),
                searchableText = obj.optString("searchableText").take(100_000),
                createdAt = obj.optLong("createdAt"),
                reminderAt = obj.optLong("reminderAt", -1).takeIf { it > System.currentTimeMillis() }
            )
        }
        return items to media
    }

    private fun encodeSettings() = JSONObject()
        .put("theme", settings.themeMode.name).put("dynamicColor", settings.dynamicColor)
        .put("assistantStyle", settings.assistantStyle.name).put("ttsVoice", settings.ttsVoice)
        .put("speechRate", settings.ttsSpeechRate).put("pitch", settings.ttsPitch)
        .put("autoSpeak", settings.autoSpeakReplies).put("userName", settings.userName)
        .put("personality", settings.aiPersonality).put("temperature", settings.temperature)
        .put("smartMode", settings.smartMode.name)

    private fun encodeTasks(items: List<LocalTask>) = JSONArray().also { array ->
        items.take(1_000).forEach { task -> array.put(JSONObject().put("id", task.id).put("title", task.title)
            .put("notes", task.notes).put("createdAt", task.createdAt)
            .put("dueAt", task.dueAt ?: -1).put("completedAt", task.completedAt ?: -1)) }
    }

    private fun decodeTasks(array: JSONArray): List<LocalTask> {
        require(array.length() <= 1_000) { "Backup contains too many tasks." }
        return List(array.length()) { index -> array.getJSONObject(index).let { obj ->
            val title = obj.getString("title")
            val notes = obj.optString("notes")
            require(title.isNotBlank() && title.length <= 180 && notes.length <= 2_000) { "A task in this backup is invalid." }
            LocalTask(
                id = obj.getString("id").take(160), title = title, notes = notes,
                createdAt = obj.optLong("createdAt"),
                dueAt = obj.optLong("dueAt", -1).takeIf { it > 0 },
                completedAt = obj.optLong("completedAt", -1).takeIf { it > 0 }
            )
        } }
    }

    private fun applySettings(value: JSONObject?) {
        if (value == null) return
        runCatching { settings.themeMode = ThemeMode.valueOf(value.optString("theme")) }
        settings.dynamicColor = value.optBoolean("dynamicColor", settings.dynamicColor)
        runCatching { settings.assistantStyle = AssistantStyle.valueOf(value.optString("assistantStyle")) }
        settings.ttsVoice = value.optString("ttsVoice").take(240)
        settings.ttsSpeechRate = value.optDouble("speechRate", 1.0).toFloat().coerceIn(.5f, 2f)
        settings.ttsPitch = value.optDouble("pitch", 1.0).toFloat().coerceIn(.5f, 2f)
        settings.autoSpeakReplies = value.optBoolean("autoSpeak", settings.autoSpeakReplies)
        settings.userName = value.optString("userName", settings.userName).take(80)
        settings.aiPersonality = value.optString("personality", settings.aiPersonality).take(160)
        settings.temperature = value.optDouble("temperature", .7).toFloat().coerceIn(0f, 1f)
        runCatching { settings.smartMode = SmartMode.valueOf(value.optString("smartMode")) }
    }

    private fun encodeWritingProfile(profile: WritingProfile) = JSONObject()
        .put("enabled", profile.enabled).put("tone", profile.tone.name).put("length", profile.length.name)
        .put("contractions", profile.contractions).put("emoji", profile.emoji)
        .put("signOff", profile.signOff).put("avoidPhrases", profile.avoidPhrases)

    private fun decodeWritingProfile(value: JSONObject) = WritingProfile(
        enabled = value.optBoolean("enabled"),
        tone = runCatching { DraftTone.valueOf(value.optString("tone")) }.getOrDefault(DraftTone.NATURAL),
        length = runCatching { DraftLength.valueOf(value.optString("length")) }.getOrDefault(DraftLength.BALANCED),
        contractions = value.optBoolean("contractions", true),
        emoji = value.optBoolean("emoji"),
        signOff = value.optString("signOff"),
        avoidPhrases = value.optString("avoidPhrases")
    ).normalized()

    private fun preview(root: JSONObject, mediaCount: Int): BackupPreview {
        val knowledge = root.getJSONArray("knowledge")
        var memories = 0
        for (index in 0 until knowledge.length()) if (knowledge.getJSONObject(index).optBoolean("memory")) memories++
        return BackupPreview(
            createdAt = root.optLong("createdAt"),
            conversations = root.getJSONArray("conversations").length(),
            memories = memories,
            documents = knowledge.length() - memories,
            savedItems = root.getJSONArray("savedItems").length(),
            mediaFiles = mediaCount,
            tasks = root.optJSONArray("tasks")?.length() ?: 0
        )
    }

    private fun InputStream.readBounded(maxBytes: Int): ByteArray {
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        while (true) {
            val read = read(buffer)
            if (read < 0) break
            require(output.size() + read <= maxBytes) { "A backup entry exceeds its safety limit." }
            output.write(buffer, 0, read)
        }
        return output.toByteArray()
    }

    companion object {
        private const val FORMAT = "jarvis-local-backup"
        private const val MANIFEST = "manifest.json"
        private const val MAX_MANIFEST_BYTES = 20 * 1024 * 1024
        private const val MAX_MEDIA_BYTES = 12L * 1024 * 1024
        private const val MAX_ARCHIVE_CONTENT_BYTES = 32L * 1024 * 1024
        private const val MAX_ZIP_ENTRIES = 1_100
    }
}
