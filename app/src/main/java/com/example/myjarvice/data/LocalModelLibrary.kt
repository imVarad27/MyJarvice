package com.example.myjarvice.data

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.util.AtomicFile
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.util.UUID

class LocalModelLibrary(private val context: Context) {
    val directory get() = File(context.filesDir, "models").apply { mkdirs() }
    fun models(): List<File> = directory.listFiles()?.filter { it.isFile && it.extension.equals("litertlm", true) }
        ?.sortedWith(compareBy<File> { it.name != "jarvis-on-device.litertlm" }.thenBy { it.name }) ?: emptyList()
    fun activeModel(): File? = SettingsStore(context).onDeviceModelPath.takeIf { it.isNotBlank() }?.let(::File)
        ?.takeIf { it.isFile } ?: models().firstOrNull { it.name == "jarvis-on-device.litertlm" }
    fun fallbackModel(): File? {
        val settings = SettingsStore(context)
        val saved = settings.localFallbackModelPath.takeIf { it.isNotBlank() }?.let(::File)?.takeIf { it.isFile }
        if (saved != null) return saved
        val fallback = models().firstOrNull { it.name == "jarvis-on-device.litertlm" } ?: activeModel() ?: models().firstOrNull()
        fallback?.let { settings.localFallbackModelPath = it.path }
        return fallback
    }
    fun fingerprint(model: File) = "${model.name}:${model.length()}:${model.lastModified()}"

    suspend fun importCandidate(uri: Uri): File {
        val resolver = context.contentResolver
        val sourceName = resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
            if (it.moveToFirst()) it.getString(0) else null
        } ?: uri.lastPathSegment.orEmpty()
        require(sourceName.endsWith(".litertlm", true)) { "Choose a .litertlm model, not a document or APK." }
        val safeName = sourceName.substringBeforeLast('.').replace(Regex("[^A-Za-z0-9._-]"), "_").take(70)
        val destination = File(directory, "$safeName-${UUID.randomUUID().toString().take(8)}.litertlm")
        require(destination.canonicalFile.parentFile == directory.canonicalFile)
        val atomic = AtomicFile(destination)
        val output = atomic.startWrite()
        try {
            resolver.openInputStream(uri)?.use { input ->
                var total = 0L
                val buffer = ByteArray(1024 * 1024)
                while (true) {
                    currentCoroutineContext().ensureActive()
                    val count = input.read(buffer)
                    if (count < 0) break
                    total += count
                    require(total <= 2L * 1024 * 1024 * 1024) { "Model exceeds the 2 GB import limit for this phone workflow." }
                    require(directory.usableSpace > count + 128L * 1024 * 1024) { "Not enough free storage to finish importing." }
                    output.write(buffer, 0, count)
                }
                require(total >= 1024 * 1024) { "This file is too small to be a supported language model." }
            } ?: error("Unable to read the selected model.")
            atomic.finishWrite(output)
        } catch (error: Throwable) { atomic.failWrite(output); throw error }
        return destination
    }

    private fun reportFile(model: File) = AtomicFile(File(context.filesDir, "model-benchmarks/${model.name}.json").apply { parentFile?.mkdirs() })
    fun report(model: File): BenchmarkReport? = runCatching {
        Json.decodeFromString<BenchmarkReport>(String(reportFile(model).readFully(), Charsets.UTF_8))
            .takeIf { it.fingerprint == fingerprint(model) && it.suiteVersion == LocalModelBenchmark.VERSION }
    }.getOrNull()
    fun save(model: File, report: BenchmarkReport) {
        val atomic = reportFile(model)
        val output = atomic.startWrite()
        try { output.write(Json.encodeToString(report).toByteArray()); atomic.finishWrite(output) }
        catch (error: Exception) { atomic.failWrite(output); throw error }
    }
}
