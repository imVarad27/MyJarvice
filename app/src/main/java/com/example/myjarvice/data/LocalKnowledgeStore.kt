package com.example.myjarvice.data

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.util.AtomicFile
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

data class KnowledgeEntry(val id: String, val name: String, val text: String, val memory: Boolean)
data class KnowledgeHit(val source: String, val text: String)

/** Explicitly saved facts and imported text only. Never included in host payloads. */
class LocalKnowledgeStore(private val context: Context) {
    private val file get() = AtomicFile(File(context.filesDir, "local-knowledge.json"))

    fun entries(): List<KnowledgeEntry> = synchronized(lock) {
        if (!file.baseFile.exists()) return@synchronized emptyList()
        val array = JSONArray(String(file.readFully(), Charsets.UTF_8))
        List(array.length()) { i -> array.getJSONObject(i).let {
            KnowledgeEntry(it.getString("id"), it.getString("name"), it.getString("text"), it.getBoolean("memory"))
        } }
    }

    private fun save(entries: List<KnowledgeEntry>) {
        val json = JSONArray()
        entries.forEach { json.put(JSONObject().put("id", it.id).put("name", it.name)
            .put("text", it.text).put("memory", it.memory)) }
        val atomic = file
        val output = atomic.startWrite()
        try {
            output.write(json.toString().toByteArray(Charsets.UTF_8))
            atomic.finishWrite(output)
        } catch (e: Exception) { atomic.failWrite(output); throw e }
    }

    fun remember(text: String): KnowledgeEntry = synchronized(lock) {
        val fact = text.trim()
        require(fact.isNotEmpty() && fact.length <= 300) { "Use 1–300 characters per memory." }
        val all = entries()
        all.firstOrNull { it.memory && it.text == fact }?.let { return@synchronized it }
        require(all.count { it.memory } < 50) { "Memory is full. Delete a saved fact first (maximum 50)." }
        KnowledgeEntry(UUID.randomUUID().toString(), "Saved memory", fact, true).also { save(all + it) }
    }

    fun delete(id: String) = synchronized(lock) { save(entries().filterNot { it.id == id }) }

    fun importDocument(uri: Uri): KnowledgeEntry {
        val resolver = context.contentResolver
        val name = resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
            if (it.moveToFirst()) it.getString(0) else null
        } ?: uri.lastPathSegment?.substringAfterLast('/') ?: "Document"
        val pdf = name.endsWith(".pdf", true) || resolver.getType(uri) == "application/pdf"
        require(pdf || name.endsWith(".txt", true) || name.endsWith(".md", true)) {
            "Choose a PDF, TXT or Markdown file."
        }
        // Bound input before parsing, including providers that report no file size.
        val bytes = resolver.openInputStream(uri)?.use { input ->
            val output = java.io.ByteArrayOutputStream()
            val buffer = ByteArray(8192)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                require(output.size() + count <= 5 * 1024 * 1024) { "Choose a document smaller than 5 MB." }
                output.write(buffer, 0, count)
            }
            output.toByteArray()
        } ?: error("Cannot read this document.")
        val text = if (pdf) {
            PDFBoxResourceLoader.init(context.applicationContext)
            PDDocument.load(bytes).use { doc ->
                require(!doc.isEncrypted) { "Choose an unencrypted PDF." }
                require(doc.numberOfPages <= 50) { "Choose a PDF with at most 50 pages." }
                val stripper = PDFTextStripper()
                buildString {
                    for (page in 1..doc.numberOfPages) {
                        stripper.startPage = page
                        stripper.endPage = page
                        append("\n[Page $page]\n")
                        val pageText = stripper.getText(doc)
                        require(length + pageText.length <= 100_000) { "Document text exceeds 100,000 characters." }
                        append(pageText)
                    }
                }.also { extracted ->
                    require(extracted.replace(Regex("\\[Page \\d+\\]"), "").isNotBlank()) {
                        "No readable text found. Scanned PDFs need OCR and are not supported yet."
                    }
                }
            }
        } else bytes.toString(Charsets.UTF_8)
        require(text.isNotBlank() && text.length <= 100_000 && '\u0000' !in text) {
            "Choose a text document with 1–100,000 characters."
        }
        return synchronized(lock) {
            val all = entries()
            require(all.count { !it.memory } < 20) { "Library is full. Remove a document first (maximum 20)." }
            KnowledgeEntry(UUID.randomUUID().toString(), name.take(160), text, false).also { save(all + it) }
        }
    }

    fun search(query: String): List<KnowledgeHit> = rank(query, entries())

    companion object {
        private val lock = Any()
        private val stopWords = setOf("the", "is", "a", "an", "in", "to", "of", "and", "what", "my", "me", "about", "does", "say", "please", "from", "document")
        private fun terms(text: String) = Regex("[\\p{L}\\p{N}]+").findAll(text.lowercase())
            .map { it.value }.filter { it.length > 1 && it !in stopWords }.toSet()

        /** Bounded lexical retrieval: no extra embedding model or network required. */
        fun rank(query: String, entries: List<KnowledgeEntry>): List<KnowledgeHit> {
            val keywords = terms(query)
            if (keywords.isEmpty()) return emptyList()
            return entries.flatMap { entry ->
                entry.text.windowed(700, 550, partialWindows = true).mapIndexed { index, chunk ->
                    val score = terms(chunk + " " + entry.name).intersect(keywords).size
                    score to KnowledgeHit(
                        if (entry.memory) "Saved memory" else "${entry.name} · passage ${index + 1}", chunk
                    )
                }
            }.filter { it.first > 0 }.sortedByDescending { it.first }.take(3).map { it.second }
        }
    }
}
