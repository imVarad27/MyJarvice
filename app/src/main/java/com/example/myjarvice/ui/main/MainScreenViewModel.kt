package com.example.myjarvice.ui.main

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.myjarvice.data.ConnectionStatus
import com.example.myjarvice.data.DeviceActionExecutor
import com.example.myjarvice.data.DeviceContextProvider
import com.example.myjarvice.data.JarviceMessage
import com.example.myjarvice.data.JarviceWebSocketClient
import com.example.myjarvice.data.PendingEmail
import com.example.myjarvice.data.SettingsStore
import com.example.myjarvice.data.SpeechManager
import com.example.myjarvice.data.VoiceOption
import com.example.myjarvice.wake.WakeEvents
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class MainScreenViewModel(application: Application) : AndroidViewModel(application) {

    val wsClient = JarviceWebSocketClient()
    val deviceContext = DeviceContextProvider(application.applicationContext)
    val speechManager = SpeechManager(application.applicationContext)
    private val actionExecutor = DeviceActionExecutor(application.applicationContext)

    val connectionStatus: StateFlow<ConnectionStatus> = wsClient.connectionStatus
    val chatHistory: StateFlow<List<JarviceMessage>> = wsClient.chatHistory
    val isSpeaking: StateFlow<Boolean> = speechManager.isSpeaking
    val isListening: StateFlow<Boolean> = speechManager.isListening

    private val settings = SettingsStore(application.applicationContext)

    // Restored from disk so the link comes back by itself on every launch.
    private val _serverIp = MutableStateFlow(settings.serverIp)
    val serverIp: StateFlow<String> = _serverIp.asStateFlow()

    /** Full-screen, hands-free voice mode (the ChatGPT-style orb screen). */
    private val _voiceModeActive = MutableStateFlow(false)
    val voiceModeActive: StateFlow<Boolean> = _voiceModeActive.asStateFlow()

    /** While muted, voice mode stays open but the recogniser is not restarted. */
    private val _micMuted = MutableStateFlow(false)
    val micMuted: StateFlow<Boolean> = _micMuted.asStateFlow()

    /** True between sending a query and the reply landing, so the UI can show progress. */
    private val _isThinking = MutableStateFlow(false)
    val isThinking: StateFlow<Boolean> = _isThinking.asStateFlow()

    /** Non-null while an email draft is waiting on the user's yes/no. */
    val pendingEmail: StateFlow<PendingEmail?> = wsClient.pendingEmail

    val micLevel: StateFlow<Float> = speechManager.micLevel
    val voices: StateFlow<List<VoiceOption>> = speechManager.voices
    val selectedVoiceId: StateFlow<String> = speechManager.selectedVoiceId

    init {
        wsClient.connect(_serverIp.value)

        viewModelScope.launch {
            wsClient.latestResponse.collect { msg ->
                msg?.let {
                    _isThinking.value = false
                    // Case-insensitive so both the server's "JARVIS" and the client's
                    // "Jarvis (Offline)" placeholder are spoken.
                    if (it.sender.startsWith("JARVIS", ignoreCase = true)) {
                        speechManager.speak(it.text)
                    }
                }
            }
        }

        // Execute phone actions (call / open app) the server directs.
        viewModelScope.launch {
            wsClient.latestAction.collect { action ->
                action?.let { actionExecutor.execute(it) }
            }
        }

        // When opened by the "Jarvis" wake word, drop straight into voice mode.
        viewModelScope.launch {
            WakeEvents.voiceTrigger.collect { triggered ->
                if (triggered) {
                    WakeEvents.voiceTrigger.value = false
                    enterVoiceMode()
                }
            }
        }

        // Hands-free turn taking: once JARVICE finishes speaking, listen again.
        viewModelScope.launch {
            speechManager.isSpeaking.collect { speaking ->
                if (!speaking && _voiceModeActive.value && !_micMuted.value &&
                    !isListening.value && !_isThinking.value
                ) {
                    beginListening()
                }
            }
        }
    }

    /** Single entry point for listening, so every path re-arms the same way. */
    private fun beginListening() {
        speechManager.startListening(
            onResult = { voiceText -> sendQuery(voiceText) },
            onNoResult = {
                // Silence or an engine hiccup ended the turn. In voice mode that must not
                // leave the mic dead, so pick it back up after a short breath.
                viewModelScope.launch {
                    delay(RELISTEN_DELAY_MS)
                    // Never re-open the mic while a reply is still in flight; otherwise
                    // room noise fires a second query on top of the one being answered.
                    if (_voiceModeActive.value && !_micMuted.value &&
                        !isSpeaking.value && !isListening.value && !_isThinking.value
                    ) {
                        beginListening()
                    }
                }
            }
        )
    }

    fun enterVoiceMode() {
        _voiceModeActive.value = true
        _micMuted.value = false
        if (!isListening.value && !isSpeaking.value) {
            beginListening()
        }
    }

    fun exitVoiceMode() {
        _voiceModeActive.value = false
        _isThinking.value = false
        speechManager.stopListening()
        speechManager.stopSpeaking()
    }

    fun toggleMute() {
        val muted = !_micMuted.value
        _micMuted.value = muted
        if (muted) {
            speechManager.stopListening()
        } else if (!isSpeaking.value) {
            beginListening()
        }
    }

    fun selectVoice(voiceId: String) = speechManager.applyVoice(voiceId)

    /** Nothing leaves the host server until this is called with approved = true. */
    fun resolvePendingEmail(id: String, approved: Boolean) =
        wsClient.resolvePendingEmail(id, approved)

    /** Plain-text transcript for voice mode's share action. */
    fun buildTranscript(): String =
        chatHistory.value.joinToString("\n\n") { msg ->
            val who = if (msg.sender == "USER") "You" else "Jarvis"
            "$who: ${msg.text}"
        }

    fun updateServerIp(newIp: String) {
        val trimmed = newIp.trim()
        _serverIp.value = trimmed
        settings.serverIp = trimmed
        wsClient.updateServerIp(trimmed)
    }

    fun sendQuery(text: String) {
        if (text.isNotBlank()) {
            _isThinking.value = true
            val ctx = deviceContext.getDeviceContext()
            wsClient.sendMessage(text, ctx)
        }
    }

    /** Push-to-talk from the chat screen, without entering full-screen voice mode. */
    fun toggleVoiceInput() {
        if (isListening.value) {
            speechManager.stopListening()
        } else {
            beginListening()
        }
    }

    override fun onCleared() {
        super.onCleared()
        wsClient.disconnect()
        speechManager.shutdown()
    }

    private companion object {
        const val RELISTEN_DELAY_MS = 700L
    }
}
