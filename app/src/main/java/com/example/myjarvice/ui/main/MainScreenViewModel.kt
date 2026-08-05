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

        // When opened by the "Jarvis" wake word, immediately start listening.
        viewModelScope.launch {
            WakeEvents.voiceTrigger.collect { triggered ->
                if (triggered) {
                    WakeEvents.voiceTrigger.value = false
                    if (!isListening.value) {
                        speechManager.startListening { voiceText -> sendQuery(voiceText) }
                    }
                }
            }
        }
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
