package com.example.myjarvice.wake

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.vosk.Model
import org.vosk.Recognizer

/** Enrollment must contain the exact wake phrase, not arbitrary noise or speech. */
object WakeEnrollmentValidator {
    suspend fun check(context: Context, audio: ShortArray): Boolean = withContext(Dispatchers.IO) {
        val directory = WakeModelStore.prepare(context)
        val model = Model(directory.absolutePath)
        var recognizer: Recognizer? = null
        try {
            val local = Recognizer(model, 16000f).apply { setWords(true) }
            recognizer = local
            val completed = local.acceptWaveForm(audio, audio.size)
            val result = JSONObject(if (completed) local.result else local.finalResult)
            val words = result.optJSONArray("result") ?: return@withContext false
            WakePhrase.matches(result.optString("text")) && WakePhrase.confidentWords(
                (0 until words.length()).map { index ->
                    val word = words.getJSONObject(index)
                    word.optString("word") to word.optDouble("conf", 0.0)
                }
            )
        } finally {
            recognizer?.close()
            model.close()
        }
    }
}
