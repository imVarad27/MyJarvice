package com.example.myjarvice.data

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.util.Locale

class SpeechManager(private val context: Context) : TextToSpeech.OnInitListener {

    private var tts: TextToSpeech? = TextToSpeech(context, this)
    private var speechRecognizer: SpeechRecognizer? = null

    private val _isSpeaking = MutableStateFlow(false)
    val isSpeaking: StateFlow<Boolean> = _isSpeaking

    private val _isListening = MutableStateFlow(false)
    val isListening: StateFlow<Boolean> = _isListening

    private val _recognizedText = MutableStateFlow("")
    val recognizedText: StateFlow<String> = _recognizedText

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            tts?.language = Locale.US
            tts?.setPitch(0.95f) // Crisp, sophisticated JARVIS tone
            tts?.setSpeechRate(1.05f)
        }
    }

    fun speak(text: String) {
        if (tts != null && text.isNotBlank()) {
            _isSpeaking.value = true
            tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "JARVICE_TTS")
        }
    }

    fun stopSpeaking() {
        tts?.stop()
        _isSpeaking.value = false
    }

    fun startListening(onResult: (String) -> Unit) {
        if (!SpeechRecognizer.isRecognitionAvailable(context)) return

        speechRecognizer?.destroy()
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context).apply {
            setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) { _isListening.value = true }
                override fun onBeginningOfSpeech() {}
                override fun onRmsChanged(rmsdB: Float) {}
                override fun onBufferReceived(buffer: ByteArray?) {}
                override fun onEndOfSpeech() { _isListening.value = false }
                override fun onError(error: Int) { _isListening.value = false }
                override fun onResults(results: Bundle?) {
                    _isListening.value = false
                    val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    if (!matches.isNullOrEmpty()) {
                        val text = matches[0]
                        _recognizedText.value = text
                        onResult(text)
                    }
                }
                override fun onPartialResults(partialResults: Bundle?) {}
                override fun onEvent(eventType: Int, params: Bundle?) {}
            })
        }

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.US)
        }
        speechRecognizer?.startListening(intent)
    }

    fun stopListening() {
        speechRecognizer?.stopListening()
        _isListening.value = false
    }

    fun shutdown() {
        tts?.stop()
        tts?.shutdown()
        speechRecognizer?.destroy()
    }
}
