package com.example.myjarvice.data

import android.content.Context
import android.util.AtomicFile
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

/** A reusable, phone-only checklist. A routine never starts actions or creates tasks on its own. */
data class LocalRoutine(
    val id: String,
    val title: String,
    val steps: List<String>,
    val createdAt: Long,
    val updatedAt: Long
)

internal object RoutinePlan {
    const val MAX_ROUTINES = 100
    const val MAX_STEPS = 25
    const val MAX_TITLE_LENGTH = 80
    const val MAX_STEP_LENGTH = 180

    fun normalizedSteps(steps: List<String>): List<String> = steps.map { it.trim() }.filter { it.isNotEmpty() }

    fun validate(title: String, steps: List<String>) {
        require(title.trim().isNotEmpty() && title.trim().length <= MAX_TITLE_LENGTH) {
            "Use a routine name of 1–$MAX_TITLE_LENGTH characters."
        }
        require(steps.isNotEmpty() && steps.size <= MAX_STEPS) {
            "A routine needs 1–$MAX_STEPS steps."
        }
        require(steps.all { it.length <= MAX_STEP_LENGTH }) {
            "Each step can contain up to $MAX_STEP_LENGTH characters."
        }
    }
}

class LocalRoutineStore(context: Context) {
    private val file = AtomicFile(File(context.filesDir, "local-routines.json"))

    fun routines(): List<LocalRoutine> = synchronized(lock) {
        if (!file.baseFile.exists()) return@synchronized emptyList()
        runCatching {
            val array = JSONArray(String(file.readFully(), Charsets.UTF_8))
            List(array.length()) { index -> decode(array.getJSONObject(index)) }
                .sortedBy { it.title.lowercase() }
        }.getOrDefault(emptyList())
    }

    fun saveRoutine(id: String? = null, title: String, steps: List<String>): LocalRoutine = synchronized(lock) {
        val cleanTitle = title.trim()
        val cleanSteps = RoutinePlan.normalizedSteps(steps)
        RoutinePlan.validate(cleanTitle, cleanSteps)
        val all = routines().toMutableList()
        val existing = id?.let { routineId -> all.firstOrNull { it.id == routineId } }
        val now = System.currentTimeMillis()
        val routine = if (existing == null) {
            require(all.size < RoutinePlan.MAX_ROUTINES) { "Routine library is full. Delete a routine before adding another." }
            LocalRoutine(UUID.randomUUID().toString(), cleanTitle, cleanSteps, now, now)
        } else existing.copy(title = cleanTitle, steps = cleanSteps, updatedAt = now)
        val index = all.indexOfFirst { it.id == routine.id }
        if (index >= 0) all[index] = routine else all += routine
        persist(all)
        routine
    }

    fun delete(id: String) = synchronized(lock) { persist(routines().filterNot { it.id == id }) }

    fun mergeRoutines(incoming: List<LocalRoutine>): Int = synchronized(lock) {
        val current = routines().toMutableList()
        val existing = current.mapTo(mutableSetOf()) { it.id }
        var added = 0
        incoming.take(RoutinePlan.MAX_ROUTINES).forEach { routine ->
            if (routine.id.isBlank() || routine.id.length > 160 || routine.id in existing) return@forEach
            val title = routine.title.trim()
            val steps = RoutinePlan.normalizedSteps(routine.steps)
            if (runCatching { RoutinePlan.validate(title, steps) }.isFailure || current.size >= RoutinePlan.MAX_ROUTINES) return@forEach
            current += routine.copy(title = title, steps = steps)
            existing += routine.id
            added++
        }
        persist(current)
        added
    }

    private fun decode(value: JSONObject): LocalRoutine {
        val steps = value.optJSONArray("steps") ?: JSONArray()
        val decoded = List(steps.length()) { steps.optString(it) }
        val title = value.getString("title").trim()
        val cleanSteps = RoutinePlan.normalizedSteps(decoded)
        RoutinePlan.validate(title, cleanSteps)
        return LocalRoutine(
            id = value.getString("id").take(160), title = title, steps = cleanSteps,
            createdAt = value.optLong("createdAt"), updatedAt = value.optLong("updatedAt")
        )
    }

    private fun persist(routines: List<LocalRoutine>) {
        val array = JSONArray()
        routines.forEach { routine -> array.put(JSONObject().put("id", routine.id).put("title", routine.title)
            .put("steps", JSONArray(routine.steps)).put("createdAt", routine.createdAt).put("updatedAt", routine.updatedAt)) }
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
