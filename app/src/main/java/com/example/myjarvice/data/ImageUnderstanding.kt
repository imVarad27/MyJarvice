package com.example.myjarvice.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.ByteArrayOutputStream
import kotlin.coroutines.resume

/** A compact chat-safe photo plus any readable text found entirely on the phone. */
data class PhotoAttachment(
    val name: String,
    val bitmap: Bitmap,
    val base64: String,
    val mimeType: String = "image/jpeg",
    val ocrText: String = ""
) {
    val dataUrl: String get() = "data:$mimeType;base64,$base64"
    val hasReadableText: Boolean get() = ocrText.isNotBlank()
}

object ImageUnderstanding {
    private const val MAX_EDGE = 1280
    private const val MAX_OCR_CHARS = 6_000
    private const val MAX_UPLOAD_BYTES = 1_550_000

    suspend fun prepare(context: Context, uri: Uri): Result<PhotoAttachment> = runCatching {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        require(bounds.outWidth > 0 && bounds.outHeight > 0) { "This file is not a supported image." }
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / sample > MAX_EDGE * 2) sample *= 2
        val bitmap = context.contentResolver.openInputStream(uri)?.use { input ->
            BitmapFactory.decodeStream(input, null, BitmapFactory.Options().apply { inSampleSize = sample })
        } ?: error("Jarvis could not read that image.")
        val orientation = runCatching {
            context.contentResolver.openInputStream(uri)?.use {
                android.media.ExifInterface(it).getAttributeInt(android.media.ExifInterface.TAG_ORIENTATION, 1)
            } ?: 1
        }.getOrDefault(1)
        val matrix = android.graphics.Matrix().apply {
            when (orientation) {
                2 -> setScale(-1f, 1f)
                3 -> setRotate(180f)
                4 -> setScale(1f, -1f)
                5 -> { setRotate(90f); postScale(-1f, 1f) }
                6 -> setRotate(90f)
                7 -> { setRotate(-90f); postScale(-1f, 1f) }
                8 -> setRotate(-90f)
            }
        }
        val upright = if (orientation in 2..8) Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true) else bitmap
        val scaled = scaleDown(upright)
        if (upright !== bitmap && upright !== scaled) upright.recycle()
        if (scaled !== bitmap) bitmap.recycle()

        val bytes = compressForChat(scaled)
        val ocrText = readText(scaled)
        PhotoAttachment(
            name = displayName(context, uri),
            bitmap = scaled,
            base64 = Base64.encodeToString(bytes, Base64.NO_WRAP),
            ocrText = ocrText.take(MAX_OCR_CHARS)
        )
    }

    private fun scaleDown(source: Bitmap): Bitmap {
        val longest = maxOf(source.width, source.height)
        if (longest <= MAX_EDGE) return source
        val ratio = MAX_EDGE.toFloat() / longest
        return Bitmap.createScaledBitmap(
            source,
            (source.width * ratio).toInt().coerceAtLeast(1),
            (source.height * ratio).toInt().coerceAtLeast(1),
            true
        )
    }

    private fun compressForChat(bitmap: Bitmap): ByteArray {
        var quality = 82
        var bytes: ByteArray
        do {
            bytes = ByteArrayOutputStream().use { output ->
                check(bitmap.compress(Bitmap.CompressFormat.JPEG, quality, output)) { "Could not prepare the photo." }
                output.toByteArray()
            }
            quality -= 12
        } while (bytes.size > MAX_UPLOAD_BYTES && quality >= 46)
        return bytes
    }

    private suspend fun readText(bitmap: Bitmap): String = suspendCancellableCoroutine { continuation ->
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        recognizer.process(InputImage.fromBitmap(bitmap, 0))
            .addOnSuccessListener { result ->
                if (continuation.isActive) continuation.resume(result.text.trim())
                recognizer.close()
            }
            .addOnFailureListener {
                if (continuation.isActive) continuation.resume("")
                recognizer.close()
            }
        continuation.invokeOnCancellation { recognizer.close() }
    }

    private fun displayName(context: Context, uri: Uri): String {
        return context.contentResolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
            if (it.moveToFirst()) it.getString(0) else null
        } ?: "Photo"
    }
}
