package com.example.myjarvice.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.util.Base64
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

class NeuralAudioPlayer(private val context: Context) {
    private val TAG = "NeuralAudioPlayer"
    private var mediaPlayer: MediaPlayer? = null
    private val scope = CoroutineScope(Dispatchers.Main)
    private var visualizerJob: Job? = null

    private val _isSpeaking = MutableStateFlow(false)
    val isSpeaking: StateFlow<Boolean> = _isSpeaking

    private val _audioLevel = MutableStateFlow(0f)
    val audioLevel: StateFlow<Float> = _audioLevel

    fun playBase64Audio(base64Audio: String, onCompletion: () -> Unit = {}) {
        stop()

        scope.launch(Dispatchers.IO) {
            try {
                val audioBytes = Base64.decode(base64Audio, Base64.DEFAULT)
                val tempFile = File(context.cacheDir, "jarvis_neural_${System.currentTimeMillis()}.mp3")
                FileOutputStream(tempFile).use { it.write(audioBytes) }

                withContext(Dispatchers.Main) {
                    try {
                        mediaPlayer = MediaPlayer().apply {
                            setAudioAttributes(
                                AudioAttributes.Builder()
                                    .setUsage(AudioAttributes.USAGE_MEDIA)
                                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                                    .build()
                            )
                            setDataSource(tempFile.absolutePath)
                            prepare()
                            setOnCompletionListener {
                                _isSpeaking.value = false
                                visualizerJob?.cancel()
                                _audioLevel.value = 0f
                                tempFile.delete()
                                onCompletion()
                            }
                            setOnErrorListener { _, what, extra ->
                                Log.w(TAG, "MediaPlayer error: what=$what, extra=$extra")
                                _isSpeaking.value = false
                                visualizerJob?.cancel()
                                _audioLevel.value = 0f
                                tempFile.delete()
                                true
                            }
                            start()
                        }

                        _isSpeaking.value = true
                        startVisualizerLoop()
                    } catch (e: Exception) {
                        Log.e(TAG, "MediaPlayer start failed", e)
                        _isSpeaking.value = false
                        tempFile.delete()
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to decode/play base64 audio", e)
                withContext(Dispatchers.Main) { _isSpeaking.value = false }
            }
        }
    }

    private fun startVisualizerLoop() {
        visualizerJob?.cancel()
        visualizerJob = scope.launch {
            var tick = 0
            while (isActive && _isSpeaking.value) {
                // Synthetic organic acoustic wave simulation matching speech cadence
                val base = kotlin.math.sin(tick * 0.4) * 0.35 + 0.5
                val jitter = (kotlin.random.Random.nextFloat() * 0.3f)
                _audioLevel.value = (base.toFloat() + jitter).coerceIn(0.15f, 0.95f)
                tick++
                delay(60)
            }
            _audioLevel.value = 0f
        }
    }

    fun stop() {
        visualizerJob?.cancel()
        _audioLevel.value = 0f
        _isSpeaking.value = false
        try {
            mediaPlayer?.apply {
                if (isPlaying) stop()
                reset()
                release()
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error stopping media player: ${e.message}")
        }
        mediaPlayer = null
    }
}
