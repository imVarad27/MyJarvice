package com.example.myjarvice.data

import android.content.Context
import android.util.AtomicFile
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

data class LocalTask(
    val id: String,
    val title: String,
    val notes: String = "",
    val createdAt: Long,
    val dueAt: Long? = null,
    val completedAt: Long? = null
) {
    val completed: Boolean get() = completedAt != null
}

data class TaskAgenda(
    val open: List<LocalTask>,
    val overdue: List<LocalTask>,
    val dueToday: List<LocalTask>,
    val completed: List<LocalTask>
) {
    companion object {
        fun from(tasks: List<LocalTask>, now: Long): TaskAgenda {
            val day = LocalDayWindow.containing(now)
            val open = tasks.filterNot { it.completed }.sortedWith(compareBy<LocalTask> { it.dueAt == null }
                .thenBy { it.dueAt ?: Long.MAX_VALUE }.thenByDescending { it.createdAt })
            return TaskAgenda(
                open = open,
                overdue = open.filter { it.dueAt != null && it.dueAt < day.startsAt },
                dueToday = open.filter { it.dueAt != null && it.dueAt >= day.startsAt && it.dueAt < day.endsAtExclusive },
                completed = tasks.filter { it.completed }.sortedByDescending { it.completedAt }.take(20)
            )
        }
    }
}

/** Explicit phone-only tasks. Nothing is inferred from chats or sent to a model. */
class LocalTaskStore(context: Context) {
    private val file = AtomicFile(File(context.filesDir, "local-tasks.json"))

    fun tasks(): List<LocalTask> = synchronized(lock) {
        if (!file.baseFile.exists()) return@synchronized emptyList()
        runCatching {
            val array = JSONArray(String(file.readFully(), Charsets.UTF_8))
            List(array.length()) { index -> array.getJSONObject(index).let { obj ->
                LocalTask(
                    id = obj.getString("id"),
                    title = obj.getString("title"),
                    notes = obj.optString("notes"),
                    createdAt = obj.getLong("createdAt"),
                    dueAt = obj.optLong("dueAt", -1).takeIf { it > 0 },
                    completedAt = obj.optLong("completedAt", -1).takeIf { it > 0 }
                )
            } }
        }.getOrDefault(emptyList())
    }

    fun saveTask(id: String? = null, title: String, notes: String = "", dueAt: Long? = null): LocalTask = synchronized(lock) {
        val cleanTitle = title.trim()
        val cleanNotes = notes.trim()
        require(cleanTitle.isNotEmpty() && cleanTitle.length <= 180) { "Use a task title of 1–180 characters." }
        require(cleanNotes.length <= 2_000) { "Task notes can contain up to 2,000 characters." }
        val all = tasks().toMutableList()
        val existing = id?.let { taskId -> all.firstOrNull { it.id == taskId } }
        val task = if (existing == null) {
            require(all.size < 1_000) { "Task list is full. Remove completed tasks first." }
            LocalTask(UUID.randomUUID().toString(), cleanTitle, cleanNotes, System.currentTimeMillis(), dueAt)
        } else existing.copy(title = cleanTitle, notes = cleanNotes, dueAt = dueAt)
        val index = all.indexOfFirst { it.id == task.id }
        if (index >= 0) all[index] = task else all += task
        persist(all)
        task
    }

    fun setCompleted(id: String, completed: Boolean, at: Long = System.currentTimeMillis()) = synchronized(lock) {
        persist(tasks().map { if (it.id == id) it.copy(completedAt = if (completed) at else null) else it })
    }

    fun delete(id: String) = synchronized(lock) { persist(tasks().filterNot { it.id == id }) }

    fun mergeTasks(incoming: List<LocalTask>): Int = synchronized(lock) {
        val current = tasks().toMutableList()
        val existing = current.mapTo(mutableSetOf()) { it.id }
        var added = 0
        incoming.take(1_000).forEach { task ->
            if (task.id.isBlank() || task.id.length > 160 || task.id in existing || task.title.isBlank() ||
                task.title.length > 180 || task.notes.length > 2_000 || current.size >= 1_000
            ) return@forEach
            current += task
            existing += task.id
            added++
        }
        persist(current)
        added
    }

    private fun persist(tasks: List<LocalTask>) {
        val array = JSONArray()
        tasks.forEach { task -> array.put(JSONObject().put("id", task.id).put("title", task.title)
            .put("notes", task.notes).put("createdAt", task.createdAt)
            .put("dueAt", task.dueAt ?: -1).put("completedAt", task.completedAt ?: -1)) }
        val output = file.startWrite()
        try {
            output.write(array.toString().toByteArray(Charsets.UTF_8))
            file.finishWrite(output)
        } catch (error: Exception) {
            file.failWrite(output)
            throw error
        }
    }

    companion object { private val lock = Any() }
}
