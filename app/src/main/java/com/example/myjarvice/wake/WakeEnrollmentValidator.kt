package com.example.myjarvice.wake

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.vosk.Model
import org.vosk.Recognizer

/** A prompted setup recognizer, prepared before asking the user to speak. */
class WakeEnrollmentValidator : AutoCloseable {
    private var model: Model? = null

    suspend fun prepare(context: Context) = withContext(Dispatchers.IO) {
        val directory = WakeModelStore.prepare(context)
        ensureActive()
        model = Model(directory.absolutePath)
    }

    data class Result(val accepted: Boolean, val heard: String)

    suspend fun check(audio: ShortArray): Result = withContext(Dispatchers.IO) {
        // During supervised setup the phrase is known. [unk] still allows rejection
        // of unrelated speech; the live wake recognizer and its threshold are unchanged.
        val local = Recognizer(checkNotNull(model), 16000f, "[\"hey jarvis\", \"[unk]\"]")
        try {
            local.setWords(true)
            val words = mutableListOf<Pair<String, Double>>()
            val segments = mutableListOf<String>()
            fun collect(json: String) {
                val result = JSONObject(json)
                result.optString("text").takeIf { it.isNotBlank() }?.let(segments::add)
                val recognized = result.optJSONArray("result") ?: return
                for (i in 0 until recognized.length()) {
                    val word = recognized.getJSONObject(i)
                    words += word.optString("word") to word.optDouble("conf", 0.0)
                }
            }
            // Keep all segments: a pause must not discard the start of the phrase.
            for (start in audio.indices step 1600) {
                ensureActive()
                val chunk = audio.copyOfRange(start, minOf(start + 1600, audio.size))
                if (local.acceptWaveForm(chunk, chunk.size)) collect(local.result)
            }
            collect(local.finalResult)
            val heard = segments.joinToString(" ")
            Result(WakePhrase.matches(heard) && WakePhrase.confidentWords(words), heard)
        } finally {
            local.close()
        }
    }

    override fun close() {
        model?.close()
        model = null
    }
}
