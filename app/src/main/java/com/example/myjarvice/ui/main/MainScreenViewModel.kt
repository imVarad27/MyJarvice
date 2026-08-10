package com.example.myjarvice.ui.main

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.myjarvice.data.ConnectionStatus
import com.example.myjarvice.data.DeviceActionExecutor
import com.example.myjarvice.data.DeviceContextProvider
import com.example.myjarvice.data.JarviceMessage
import com.example.myjarvice.data.JarviceWebSocketClient
import com.example.myjarvice.data.SpeechManager
import com.example.myjarvice.data.VoiceOption
import com.example.myjarvice.wake.WakeEvents
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
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

    private val _serverIp = MutableStateFlow("192.168.1.35") // Host PC Wi-Fi IP (or 127.0.0.1 via USB)
    val serverIp: StateFlow<String> = _serverIp.asStateFlow()

    /** Full-screen, hands-free voice mode (the ChatGPT-style orb screen). */
    private val _voiceModeActive = MutableStateFlow(false)
    val voiceModeActive: StateFlow<Boolean> = _voiceModeActive.asStateFlow()

    /** While muted, voice mode stays open but the recogniser is not restarted. */
    private val _micMuted = MutableStateFlow(false)
    val micMuted: StateFlow<Boolean> = _micMuted.asStateFlow()

    val micLevel: StateFlow<Float> = speechManager.micLevel
    val voices: StateFlow<List<VoiceOption>> = speechManager.voices
    val selectedVoiceId: StateFlow<String> = speechManager.selectedVoiceId

    init {
        wsClient.connect(_serverIp.value)

        viewModelScope.launch {
            wsClient.latestResponse.collect { msg ->
                msg?.let {
                    if (it.sender.startsWith("JARVICE")) {
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
                if (!speaking && _voiceModeActive.value && !_micMuted.value && !isListening.value) {
                    speechManager.startListening { voiceText -> sendQuery(voiceText) }
                }
            }
        }
    }

    fun enterVoiceMode() {
        _voiceModeActive.value = true
        _micMuted.value = false
        if (!isListening.value && !isSpeaking.value) {
            speechManager.startListening { voiceText -> sendQuery(voiceText) }
        }
    }

    fun exitVoiceMode() {
        _voiceModeActive.value = false
        speechManager.stopListening()
        speechManager.stopSpeaking()
    }

    fun toggleMute() {
        val muted = !_micMuted.value
        _micMuted.value = muted
        if (muted) {
            speechManager.stopListening()
        } else if (!isSpeaking.value) {
            speechManager.startListening { voiceText -> sendQuery(voiceText) }
        }
    }

    fun selectVoice(voiceId: String) = speechManager.applyVoice(voiceId)

    /** Plain-text transcript for voice mode's share action. */
    fun buildTranscript(): String =
        chatHistory.value.joinToString("\n\n") { msg ->
            val who = if (msg.sender == "USER") "You" else "JARVICE"
            "$who: ${msg.text}"
        }

    fun updateServerIp(newIp: String) {
        _serverIp.value = newIp
        wsClient.updateServerIp(newIp)
    }

    fun sendQuery(text: String) {
        if (text.isNotBlank()) {
            val ctx = deviceContext.getDeviceContext()
            wsClient.sendMessage(text, ctx)
        }
    }

    fun toggleVoiceInput() {
        if (isListening.value) {
            speechManager.stopListening()
        } else {
            speechManager.startListening { voiceText ->
                sendQuery(voiceText)
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        wsClient.disconnect()
        speechManager.shutdown()
    }
}
