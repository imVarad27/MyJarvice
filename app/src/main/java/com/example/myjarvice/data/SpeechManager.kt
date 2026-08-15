package com.example.myjarvice.data

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.util.Locale

/** A selectable TTS voice, surfaced by voice mode's "Change Voice" sheet. */
data class VoiceOption(val id: String, val label: String)

class SpeechManager(private val context: Context) : TextToSpeech.OnInitListener {

    private val settings = SettingsStore(context)

    private var tts: TextToSpeech? = TextToSpeech(context, this)
    private var speechRecognizer: SpeechRecognizer? = null

    private val _isSpeaking = MutableStateFlow(false)
    val isSpeaking: StateFlow<Boolean> = _isSpeaking

    private val _isListening = MutableStateFlow(false)
    val isListening: StateFlow<Boolean> = _isListening

    private val _recognizedText = MutableStateFlow("")
    val recognizedText: StateFlow<String> = _recognizedText

    /** Live mic amplitude in dB, used to drive the voice-mode orb. */
    private val _micLevel = MutableStateFlow(0f)
    val micLevel: StateFlow<Float> = _micLevel

    /** Populated once the TTS engine reports ready; empty until then. */
    private val _voices = MutableStateFlow<List<VoiceOption>>(emptyList())
    val voices: StateFlow<List<VoiceOption>> = _voices

    private val _selectedVoiceId = MutableStateFlow(settings.ttsVoice)
    val selectedVoiceId: StateFlow<String> = _selectedVoiceId

    /** The engine binds asynchronously; speaking before this is set silently fails. */
    private var ttsReady = false

    /** Holds an utterance that arrived before the engine finished binding. */
    private var pendingUtterance: String? = null

    override fun onInit(status: Int) {
        if (status != TextToSpeech.SUCCESS) {
            Log.w(TAG, "TTS engine failed to initialise (status=$status)")
            _isSpeaking.value = false
            return
        }

        tts?.language = Locale.US
        tts?.setPitch(0.95f) // Crisp, sophisticated JARVIS tone
        tts?.setSpeechRate(1.05f)

        // Without this the "speaking" flag latches on forever after the first
        // utterance, which strands the UI in its speaking state.
        @Suppress("OVERRIDE_DEPRECATION")
        tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) { _isSpeaking.value = true }
            override fun onDone(utteranceId: String?) { _isSpeaking.value = false }
            override fun onStop(utteranceId: String?, interrupted: Boolean) { _isSpeaking.value = false }
            override fun onError(utteranceId: String?) { _isSpeaking.value = false }
            override fun onError(utteranceId: String?, errorCode: Int) { _isSpeaking.value = false }
        })

        loadVoices()
        applyVoice(_selectedVoiceId.value)

        ttsReady = true
        // The server's greeting usually beats the engine's binding, so replay it now
        // rather than dropping it and stranding the UI in its "speaking" state.
        pendingUtterance?.let { queued ->
            pendingUtterance = null
            speak(queued)
        }
    }

    private fun loadVoices() {
        val available = runCatching { tts?.voices.orEmpty() }.getOrDefault(emptySet())
        val labelled = available
            .filter { it.locale.language == Locale.ENGLISH.language }
            .filterNot { it.isNetworkConnectionRequired }
            .sortedBy { it.name }
            .map { it to prettyVoiceLabel(it) }

        // Many engines expose several voices per locale with no gender hint in the name,
        // which would render as a list of identical rows. Number the duplicates.
        val labelCounts = labelled.groupingBy { it.second }.eachCount()
        val seen = mutableMapOf<String, Int>()

        _voices.value = labelled.map { (voice, label) ->
            val display = if (labelCounts.getValue(label) > 1) {
                val n = seen.merge(label, 1, Int::plus) ?: 1
                "$label · Voice $n"
            } else {
                label
            }
            VoiceOption(id = voice.name, label = display)
        }
    }

    /** Applies a voice by its engine name. Blank or unknown falls back to the engine default. */
    fun applyVoice(voiceId: String) {
        val engine = tts ?: return
        val match = runCatching { engine.voices.orEmpty() }
            .getOrDefault(emptySet())
            .firstOrNull { it.name == voiceId }

        if (match != null) {
            engine.voice = match
            _selectedVoiceId.value = voiceId
            settings.ttsVoice = voiceId
        } else {
            engine.language = Locale.US
            _selectedVoiceId.value = ""
            settings.ttsVoice = ""
        }
    }

    fun speak(text: String) {
        if (text.isBlank()) return

        val engine = tts
        if (engine == null || !ttsReady) {
            pendingUtterance = text
            return
        }

        // Only claim to be speaking if the engine actually accepted the utterance.
        // Setting it optimistically strands voice mode: the progress callbacks never
        // fire for a rejected utterance, so the flag would never clear and the
        // hands-free loop would wait forever for silence that never comes.
        val result = engine.speak(text, TextToSpeech.QUEUE_FLUSH, null, UTTERANCE_ID)
        if (result == TextToSpeech.SUCCESS) {
            _isSpeaking.value = true
        } else {
            Log.w(TAG, "TTS refused the utterance (result=$result)")
            _isSpeaking.value = false
        }
    }

    fun stopSpeaking() {
        tts?.stop()
        _isSpeaking.value = false
    }

    /**
     * @param onNoResult fires when recognition ended without producing text (silence,
     *   timeout, engine error). Lets hands-free mode re-arm instead of going deaf.
     */
    fun startListening(onResult: (String) -> Unit, onNoResult: () -> Unit = {}) {
        if (!SpeechRecognizer.isRecognitionAvailable(context)) return

        speechRecognizer?.destroy()
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context).apply {
            setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) { _isListening.value = true }
                override fun onBeginningOfSpeech() {}
                override fun onRmsChanged(rmsdB: Float) { _micLevel.value = rmsdB }
                override fun onBufferReceived(buffer: ByteArray?) {}
                override fun onEndOfSpeech() { _isListening.value = false }
                override fun onError(error: Int) {
                    _isListening.value = false
                    _micLevel.value = 0f
                    onNoResult()
                }
                override fun onResults(results: Bundle?) {
                    _isListening.value = false
                    _micLevel.value = 0f
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
        _micLevel.value = 0f
    }

    fun shutdown() {
        tts?.stop()
        tts?.shutdown()
        speechRecognizer?.destroy()
    }

    private companion object {
        const val TAG = "SpeechManager"
        const val UTTERANCE_ID = "JARVICE_TTS"

        /** Turns "en-us-x-sfg#female_1-local" into something like "US English · Female 1". */
        fun prettyVoiceLabel(voice: Voice): String {
            val region = voice.locale.getDisplayCountry(Locale.US)
                .ifBlank { voice.locale.getDisplayLanguage(Locale.US) }
                .ifBlank { voice.locale.toString() }

            val name = voice.name.lowercase(Locale.US)
            val gender = when {
                name.contains("female") -> "Female"
                name.contains("male") -> "Male"
                else -> null
            }
            val index = Regex("""(?:fe)?male[_-]?(\d+)""").find(name)?.groupValues?.getOrNull(1)

            val descriptor = listOfNotNull(gender, index).joinToString(" ")
            return if (descriptor.isBlank()) region else "$region · $descriptor"
        }
    }
}
