package com.example.myjarvice.data

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

enum class RememberKind { TEXT, LINK, PHOTO, VOICE }

data class RememberItem(
    val id: String,
    val kind: RememberKind,
    val title: String,
    val summary: String,
    val searchableText: String,
    val createdAt: Long,
    val mediaPath: String? = null,
    val reminderAt: Long? = null
)

/** Private, phone-only inbox for items received from Android's Share sheet. */
class RememberInboxStore(private val context: Context) {
    private val prefs = context.getSharedPreferences("remember_inbox", Context.MODE_PRIVATE)
    private val key = "items"

    fun items(): List<RememberItem> = load().sortedByDescending { it.createdAt }

    fun search(query: String): List<RememberItem> {
        val needle = query.trim().lowercase()
        return items().filter { needle.isBlank() || listOf(it.title, it.summary, it.searchableText)
            .any { value -> value.lowercase().contains(needle) } }
    }

    fun addText(text: String): RememberItem {
        val trimmed = text.trim()
        val isLink = trimmed.matches(Regex("https?://\\S+", RegexOption.IGNORE_CASE))
        val title = if (isLink) trimmed.substringAfter("//").substringBefore('/').ifBlank { "Saved link" }
            else trimmed.lineSequence().firstOrNull { it.isNotBlank() }?.take(80) ?: "Saved note"
        return add(RememberItem(
            id = UUID.randomUUID().toString(),
            kind = if (isLink) RememberKind.LINK else RememberKind.TEXT,
            title = title,
            summary = trimmed.take(260),
            searchableText = trimmed,
            createdAt = System.currentTimeMillis()
        ))
    }

    fun addPhoto(photo: PhotoAttachment): RememberItem {
        val id = UUID.randomUUID().toString()
        val target = File(inboxDir(), "$id.jpg")
        target.writeBytes(Base64.decode(photo.base64, Base64.DEFAULT))
        return add(RememberItem(
            id = id,
            kind = RememberKind.PHOTO,
            title = photo.ocrText.lineSequence().firstOrNull { it.isNotBlank() }?.take(80) ?: "Saved photo",
            summary = if (photo.hasReadableText) photo.ocrText.take(260) else "Photo saved for later",
            searchableText = photo.ocrText,
            createdAt = System.currentTimeMillis(),
            mediaPath = target.absolutePath
        ))
    }

    fun addVoice(displayName: String, bytes: ByteArray, extension: String = "m4a"): RememberItem {
        val id = UUID.randomUUID().toString()
        val target = File(inboxDir(), "$id.$extension")
        target.writeBytes(bytes)
        return add(RememberItem(
            id = id,
            kind = RememberKind.VOICE,
            title = displayName.ifBlank { "Voice note" },
            summary = "Voice note saved for later",
            searchableText = displayName,
            createdAt = System.currentTimeMillis(),
            mediaPath = target.absolutePath
        ))
    }

    fun delete(id: String) {
        val item = load().firstOrNull { it.id == id } ?: return
        item.mediaPath?.let { File(it).delete() }
        save(load().filterNot { it.id == id })
        cancelReminder(item)
    }

    fun setReminder(id: String, at: Long?) {
        require(at == null || at > System.currentTimeMillis()) { "Choose a reminder time in the future." }
        val updated = load().map { if (it.id == id) it.copy(reminderAt = at) else it }
        save(updated)
        updated.firstOrNull { it.id == id }?.let { item ->
            cancelReminder(item)
            if (at != null) scheduleReminder(item, at)
        }
    }

    private fun add(item: RememberItem): RememberItem {
        save(load() + item)
        return item
    }

    fun restoreReminders() {
        items().forEach { item -> item.reminderAt?.let { time ->
            scheduleReminder(item, maxOf(time, System.currentTimeMillis() + 1000))
        } }
    }

    /** Merge a backup. Media paths from the archive are ignored and regenerated in app-private storage. */
    fun mergeItems(incoming: List<RememberItem>, media: Map<String, Pair<String, ByteArray>>): Int {
        val current = load().toMutableList()
        val existing = current.mapTo(mutableSetOf()) { it.id }
        var added = 0
        incoming.take(1_000).forEach { item ->
            if (item.id.isBlank() || item.id.length > 160 || item.id in existing || item.title.length > 240 ||
                item.summary.length > 2_000 || item.searchableText.length > 100_000
            ) return@forEach
            val restoredPath = media[item.id]?.let { (extension, bytes) ->
                val safeExtension = extension.lowercase().filter { it.isLetterOrDigit() }.take(8).ifBlank { "bin" }
                if (bytes.size > 12 * 1024 * 1024) null else File(inboxDir(), "${item.id}.$safeExtension")
                    .also { it.writeBytes(bytes) }.absolutePath
            }
            if ((item.kind == RememberKind.PHOTO || item.kind == RememberKind.VOICE) && restoredPath == null) return@forEach
            val restored = item.copy(mediaPath = restoredPath)
            current += restored
            existing += restored.id
            if (restored.reminderAt != null && restored.reminderAt > System.currentTimeMillis()) {
                scheduleReminder(restored, restored.reminderAt)
            }
            added++
        }
        save(current)
        return added
    }

    private fun inboxDir(): File = File(context.filesDir, "remember-inbox").apply { mkdirs() }

    private fun load(): List<RememberItem> = runCatching {
        val array = JSONArray(prefs.getString(key, "[]"))
        buildList {
            for (index in 0 until array.length()) {
                val obj = array.getJSONObject(index)
                add(RememberItem(
                    id = obj.getString("id"),
                    kind = RememberKind.valueOf(obj.getString("kind")),
                    title = obj.getString("title"),
                    summary = obj.getString("summary"),
                    searchableText = obj.optString("searchableText"),
                    createdAt = obj.getLong("createdAt"),
                    mediaPath = obj.optString("mediaPath").ifBlank { null },
                    reminderAt = obj.optLong("reminderAt", -1).takeIf { it > 0 }
                ))
            }
        }
    }.getOrDefault(emptyList())

    private fun save(items: List<RememberItem>) {
        val array = JSONArray()
        items.forEach { item -> array.put(JSONObject().apply {
            put("id", item.id); put("kind", item.kind.name); put("title", item.title)
            put("summary", item.summary); put("searchableText", item.searchableText)
            put("createdAt", item.createdAt)
            if (item.mediaPath != null) put("mediaPath", item.mediaPath)
            put("reminderAt", item.reminderAt ?: -1)
        }) }
        prefs.edit().putString(key, array.toString()).apply()
    }

    private fun scheduleReminder(item: RememberItem, at: Long) {
        val alarm = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        alarm.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, reminderIntent(item))
    }

    private fun cancelReminder(item: RememberItem) {
        val alarm = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        alarm.cancel(reminderIntent(item))
    }

    private fun reminderIntent(item: RememberItem): PendingIntent = PendingIntent.getBroadcast(
        context,
        item.id.hashCode(),
        Intent(context, RememberReminderReceiver::class.java).apply {
            putExtra(RememberReminderReceiver.EXTRA_ITEM_ID, item.id)
            putExtra(RememberReminderReceiver.EXTRA_TITLE, item.title)
        },
        PendingIntent.FLAG_UPDATE_CURRENT or if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0
    )
}
