package com.example.myjarvice.data

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.util.Locale

@Serializable
data class KnowledgeEntry(val id: String, val name: String, val text: String, val memory: Boolean,
                          val enabled: Boolean = true)

enum class KnowledgeFilter(val title: String) {
    ALL("All"), MEMORIES("Memories"), DOCUMENTS("Documents"), EXCLUDED("Excluded")
}

/** Explicit user controls; exclusion is a retrieval boundary, not deletion or encryption. */
object KnowledgeLibrary {
    const val MAX_BYTES = 16 * 1024 * 1024
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val safeMessages = setOf(
        "This memory no longer exists. Refresh the library.",
        "This memory changed. Reopen it before editing.",
        "This item no longer exists. Refresh the library.",
        "This item changed. Review it again before removing it.",
        "Use 1–300 characters per memory.", "This fact is already saved.",
        "Memory is full. Delete a saved fact first (maximum 50).",
        "Library is full. Remove a document first (maximum 20).",
        "Choose a PDF, TXT or Markdown file.", "Choose a document smaller than 5 MB.",
        "Choose an unencrypted PDF.", "Choose a PDF with at most 50 pages.",
        "Document text exceeds 100,000 characters.",
        "No readable text found. Scanned PDFs need OCR and are not supported yet.",
        "Choose a text document with 1–100,000 characters.", "Cannot read this document."
    )

    /** Never show arbitrary parser/provider messages containing document text or file paths. */
    fun failureMessage(error: Throwable): String = error.message?.takeIf { it in safeMessages }
        ?: "Couldn't finish this operation. Refresh to check your library; unreadable data isn't overwritten."

    fun validate(entries: List<KnowledgeEntry>): List<KnowledgeEntry> {
        require(entries.count { it.memory } <= 50 && entries.count { !it.memory } <= 20) { "Library limit exceeded." }
        require(entries.map { it.id }.distinct().size == entries.size) { "Duplicate library IDs." }
        entries.forEach {
            require(it.id.isNotBlank() && it.id.length <= 160 && it.name.length <= 160 &&
                it.text.isNotBlank() && it.text.length <= (if (it.memory) 300 else 100_000) && '\u0000' !in it.text) {
                "Invalid library entry."
            }
        }
        return entries
    }

    fun decode(text: String): List<KnowledgeEntry> {
        require(text.toByteArray(Charsets.UTF_8).size <= MAX_BYTES) { "Library file is too large." }
        val array = json.parseToJsonElement(text) as? JsonArray ?: error("Invalid library format.")
        require(array.size <= 70) { "Library limit exceeded." }
        array.forEach { element ->
            val row = element as? JsonObject ?: error("Invalid library entry.")
            fun validBoolean(value: JsonElement?) = value is JsonPrimitive && !value.isString && value.booleanOrNull != null
            require(validBoolean(row["memory"])) { "Invalid memory type." }
            require("enabled" !in row || validBoolean(row["enabled"])) { "Invalid library retrieval setting." }
        }
        return validate(json.decodeFromJsonElement<List<KnowledgeEntry>>(array))
    }

    fun encode(entries: List<KnowledgeEntry>): String = json.encodeToString(validate(entries)).also {
        require(it.toByteArray(Charsets.UTF_8).size <= MAX_BYTES) { "Library file is too large." }
    }

    fun readBounded(input: InputStream): String {
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            require(output.size() + count <= MAX_BYTES) { "Library file is too large." }
            output.write(buffer, 0, count)
        }
        return output.toByteArray().toString(Charsets.UTF_8)
    }

    fun visible(entries: List<KnowledgeEntry>, query: String, filter: KnowledgeFilter): List<KnowledgeEntry> {
        val needle = query.trim().take(200).lowercase(Locale.ROOT)
        return entries.filter { entry ->
            val matches = when (filter) {
                KnowledgeFilter.ALL -> true
                KnowledgeFilter.MEMORIES -> entry.memory
                KnowledgeFilter.DOCUMENTS -> !entry.memory
                KnowledgeFilter.EXCLUDED -> !entry.enabled
            }
            matches && (needle.isEmpty() || needle in entry.name.lowercase(Locale.ROOT) || needle in entry.text.lowercase(Locale.ROOT))
        }
    }

    fun editMemory(entries: List<KnowledgeEntry>, id: String, text: String, expectedText: String): List<KnowledgeEntry> {
        val entry = entries.singleOrNull { it.id == id } ?: error("This memory no longer exists. Refresh the library.")
        require(entry.memory) { "Imported documents cannot be edited as memories." }
        require(entry.text == expectedText) { "This memory changed. Reopen it before editing." }
        val fact = text.trim()
        require(fact.isNotBlank() && fact.length <= 300 && '\u0000' !in fact) { "Use 1–300 characters per memory." }
        require(entries.none { it.id != id && it.memory && it.text == fact }) { "This fact is already saved." }
        return validate(entries.map { if (it.id == id) it.copy(text = fact) else it })
    }

    fun setEnabled(entries: List<KnowledgeEntry>, id: String, enabled: Boolean): List<KnowledgeEntry> {
        require(entries.any { it.id == id }) { "This item no longer exists. Refresh the library." }
        return validate(entries.map { if (it.id == id) it.copy(enabled = enabled) else it })
    }

    fun deleteReviewed(entries: List<KnowledgeEntry>, reviewed: KnowledgeEntry): List<KnowledgeEntry> {
        require(entries.any { it == reviewed }) { "This item changed. Review it again before removing it." }
        return entries.filterNot { it.id == reviewed.id }
    }
}
