package com.example.myjarvice.wake

import android.content.Context
import java.io.File
import java.net.URL
import java.util.zip.ZipInputStream

/** Download only after enabling hands-free listening. Audio stays local. */
object WakeModelStore {
    private const val NAME = "vosk-model-small-en-us-0.15"
    private fun directory(context: Context) = File(context.filesDir, "wake/$NAME")
    fun ready(context: Context) = File(directory(context), ".ready").isFile
    @Synchronized fun prepare(context: Context): File {
        val destination = directory(context)
        if (ready(context)) return destination
        val staging = File(context.filesDir, "wake/download-staging")
        staging.deleteRecursively()
        staging.mkdirs()
        try {
            val connection = URL("https://alphacephei.com/vosk/models/$NAME.zip").openConnection().apply {
                connectTimeout = 20000
                readTimeout = 30000
            }
            ZipInputStream(connection.getInputStream().buffered()).use { zip ->
                var total = 0L
                val buffer = ByteArray(32768)
                while (true) {
                    val entry = zip.nextEntry ?: break
                    val target = File(staging, entry.name).canonicalFile
                    require(target.path.startsWith(staging.canonicalPath + File.separator)) { "Invalid model archive" }
                    if (entry.isDirectory) target.mkdirs() else {
                        target.parentFile?.mkdirs()
                        target.outputStream().use { output ->
                            while (true) {
                                val count = zip.read(buffer)
                                if (count < 0) break
                                total += count
                                require(total <= 150L * 1024 * 1024) { "Model archive too large" }
                                output.write(buffer, 0, count)
                            }
                        }
                    }
                    zip.closeEntry()
                }
            }
            val extracted = File(staging, NAME)
            require(File(extracted, "am/final.mdl").isFile && File(extracted, "conf/model.conf").isFile) { "Incomplete wake model" }
            destination.deleteRecursively()
            check(extracted.renameTo(destination)) { "Cannot install wake model" }
            File(destination, ".ready").writeText("1")
            return destination
        } finally { staging.deleteRecursively() }
    }
}
