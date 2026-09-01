package com.example.myjarvice.data

import android.content.Context
import android.net.Uri
import android.os.Environment
import android.provider.OpenableColumns
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStreamWriter
import java.io.PrintWriter
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

data class PcFileEntry(
    val name: String,
    val path: String,
    val isDir: Boolean,
    val sizeBytes: Long,
    val mtime: String,
    val ext: String
)

data class BrowseResult(
    val currentPath: String,
    val parentPath: String?,
    val presets: Map<String, String>,
    val totalEntries: Int,
    val entries: List<PcFileEntry>
)

object FileTransferManager {
    private const val TAG = "JarvisFileTransfer"

    private fun getBaseUrl(serverIp: String): String {
        val clean = serverIp.trim().removePrefix("http://").removePrefix("https://").removePrefix("ws://").removePrefix("wss://")
        return "http://$clean"
    }

    /**
     * Uploads any file / photo / video / document from phone to PC's Downloads/JarvisDrop folder.
     */
    suspend fun uploadFileToPc(
        context: Context,
        uri: Uri,
        serverIp: String,
        token: String,
        onProgress: (Float) -> Unit = {}
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            var fileName = "drop_file.bin"
            var fileSize: Long = 0
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                if (cursor.moveToFirst()) {
                    if (nameIndex >= 0) fileName = cursor.getString(nameIndex) ?: fileName
                    if (sizeIndex >= 0) fileSize = cursor.getLong(sizeIndex)
                }
            }

            val boundary = "===JarvisBoundary${System.currentTimeMillis()}==="
            val lineEnd = "\r\n"
            val twoHyphens = "--"

            val url = URL("${getBaseUrl(serverIp)}/api/files/upload")
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.doInput = true
            conn.doOutput = true
            conn.useCaches = false
            conn.connectTimeout = 10000
            conn.readTimeout = 60000
            conn.setRequestProperty("Connection", "Keep-Alive")
            conn.setRequestProperty("Authorization", "Bearer $token")
            conn.setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")

            val outputStream = conn.outputStream
            val writer = PrintWriter(OutputStreamWriter(outputStream, "UTF-8"), true)

            // File Field Header
            writer.append(twoHyphens).append(boundary).append(lineEnd)
            writer.append("Content-Disposition: form-data; name=\"file\"; filename=\"").append(fileName).append("\"").append(lineEnd)
            writer.append("Content-Type: application/octet-stream").append(lineEnd)
            writer.append(lineEnd)
            writer.flush()

            // Stream File Bytes with progress
            val inputStream = context.contentResolver.openInputStream(uri)
                ?: return@withContext Result.failure(Exception("Cannot open file stream"))

            val buffer = ByteArray(8192)
            var bytesRead: Int
            var totalBytesRead: Long = 0

            while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                outputStream.write(buffer, 0, bytesRead)
                totalBytesRead += bytesRead
                if (fileSize > 0) {
                    onProgress(totalBytesRead.toFloat() / fileSize.toFloat())
                }
            }
            outputStream.flush()
            inputStream.close()

            writer.append(lineEnd)
            writer.append(twoHyphens).append(boundary).append(twoHyphens).append(lineEnd)
            writer.flush()
            writer.close()

            val responseCode = conn.responseCode
            if (responseCode == HttpURLConnection.HTTP_OK) {
                val responseText = conn.inputStream.bufferedReader().readText()
                val json = JSONObject(responseText)
                val msg = json.optString("message", "File dropped successfully on PC")
                Result.success(msg)
            } else {
                val errText = conn.errorStream?.bufferedReader()?.readText() ?: "Server error $responseCode"
                Result.failure(Exception("Upload failed: $errText"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "uploadFileToPc failed", e)
            Result.failure(e)
        }
    }

    /**
     * Queries directory listing from host PC.
     */
    suspend fun browsePcDirectory(
        serverIp: String,
        token: String,
        path: String? = null,
        preset: String? = null
    ): Result<BrowseResult> = withContext(Dispatchers.IO) {
        try {
            val queryParams = mutableListOf<String>()
            if (!path.isNullOrBlank()) queryParams.add("path=${URLEncoder.encode(path, "UTF-8")}")
            if (!preset.isNullOrBlank()) queryParams.add("preset=${URLEncoder.encode(preset, "UTF-8")}")

            val qs = if (queryParams.isNotEmpty()) "?" + queryParams.joinToString("&") else ""
            val url = URL("${getBaseUrl(serverIp)}/api/files/browse$qs")
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "GET"
            conn.connectTimeout = 6000
            conn.readTimeout = 10000
            conn.setRequestProperty("Authorization", "Bearer $token")

            val responseCode = conn.responseCode
            if (responseCode == HttpURLConnection.HTTP_OK) {
                val responseText = conn.inputStream.bufferedReader().readText()
                val obj = JSONObject(responseText)

                val currentPath = obj.getString("current_path")
                val parentPath = if (obj.has("parent_path") && !obj.isNull("parent_path")) obj.getString("parent_path") else null
                val totalEntries = obj.optInt("total_entries", 0)

                val presetsMap = mutableMapOf<String, String>()
                val presetsObj = obj.optJSONObject("presets")
                if (presetsObj != null) {
                    val keys = presetsObj.keys()
                    while (keys.hasNext()) {
                        val k = keys.next()
                        presetsMap[k] = presetsObj.getString(k)
                    }
                }

                val entriesList = mutableListOf<PcFileEntry>()
                val arr = obj.getJSONArray("entries")
                for (i in 0 until arr.length()) {
                    val item = arr.getJSONObject(i)
                    entriesList.add(
                        PcFileEntry(
                            name = item.getString("name"),
                            path = item.getString("path"),
                            isDir = item.getBoolean("is_dir"),
                            sizeBytes = item.optLong("size_bytes", 0),
                            mtime = item.optString("mtime", ""),
                            ext = item.optString("ext", "")
                        )
                    )
                }

                Result.success(BrowseResult(currentPath, parentPath, presetsMap, totalEntries, entriesList))
            } else {
                val errText = conn.errorStream?.bufferedReader()?.readText() ?: "HTTP $responseCode"
                Result.failure(Exception("Browse failed: $errText"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "browsePcDirectory failed", e)
            Result.failure(e)
        }
    }

    /**
     * Downloads a file from PC to phone's public Download directory.
     */
    suspend fun downloadFileFromPc(
        context: Context,
        serverIp: String,
        token: String,
        remotePath: String,
        onProgress: (Float) -> Unit = {}
    ): Result<File> = withContext(Dispatchers.IO) {
        try {
            val fileName = remotePath.substringAfterLast("/").substringAfterLast("\\").ifBlank { "downloaded_file.bin" }
            val qs = "?path=${URLEncoder.encode(remotePath, "UTF-8")}"
            val url = URL("${getBaseUrl(serverIp)}/api/files/download$qs")
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "GET"
            conn.connectTimeout = 8000
            conn.readTimeout = 60000
            conn.setRequestProperty("Authorization", "Bearer $token")

            val responseCode = conn.responseCode
            if (responseCode == HttpURLConnection.HTTP_OK) {
                val totalLength = conn.contentLengthLong

                val downloadDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                if (!downloadDir.exists()) downloadDir.mkdirs()

                var localFile = File(downloadDir, fileName)
                var counter = 1
                val base = fileName.substringBeforeLast(".")
                val ext = if (fileName.contains(".")) "." + fileName.substringAfterLast(".") else ""
                while (localFile.exists()) {
                    localFile = File(downloadDir, "${base}_$counter$ext")
                    counter++
                }

                val inputStream = conn.inputStream
                val outputStream = FileOutputStream(localFile)

                val buffer = ByteArray(8192)
                var bytesRead: Int
                var totalBytesRead: Long = 0

                while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                    outputStream.write(buffer, 0, bytesRead)
                    totalBytesRead += bytesRead
                    if (totalLength > 0) {
                        onProgress(totalBytesRead.toFloat() / totalLength.toFloat())
                    }
                }
                outputStream.flush()
                outputStream.close()
                inputStream.close()

                Result.success(localFile)
            } else {
                val errText = conn.errorStream?.bufferedReader()?.readText() ?: "HTTP $responseCode"
                Result.failure(Exception("Download failed: $errText"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "downloadFileFromPc failed", e)
            Result.failure(e)
        }
    }

    /**
     * Tells host PC to open the file or directory with its default application.
     */
    suspend fun openFileOnPc(
        serverIp: String,
        token: String,
        remotePath: String
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            val url = URL("${getBaseUrl(serverIp)}/api/files/open")
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.doOutput = true
            conn.connectTimeout = 6000
            conn.readTimeout = 6000
            conn.setRequestProperty("Content-Type", "application/json")
            conn.setRequestProperty("Authorization", "Bearer $token")

            val payload = JSONObject().apply { put("path", remotePath) }
            conn.outputStream.use { it.write(payload.toString().toByteArray(Charsets.UTF_8)) }

            val responseCode = conn.responseCode
            if (responseCode == HttpURLConnection.HTTP_OK) {
                val responseText = conn.inputStream.bufferedReader().readText()
                val json = JSONObject(responseText)
                Result.success(json.optString("message", "Opened on host PC"))
            } else {
                val errText = conn.errorStream?.bufferedReader()?.readText() ?: "HTTP $responseCode"
                Result.failure(Exception("Open failed: $errText"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "openFileOnPc failed", e)
            Result.failure(e)
        }
    }
}
