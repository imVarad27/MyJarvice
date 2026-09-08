package com.example.myjarvice.ui.main

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.myjarvice.audio.JarvisSoundFx
import com.example.myjarvice.audio.NeuralAudioPlayer
import com.example.myjarvice.data.ChatHistoryStore
import com.example.myjarvice.data.ChatSession
import com.example.myjarvice.data.ConnectionStatus
import com.example.myjarvice.data.DeviceActionExecutor
import com.example.myjarvice.data.DeviceContextProvider
import com.example.myjarvice.data.JarvisAction
import com.example.myjarvice.data.JarvisMessage
import com.example.myjarvice.data.JarvisWebSocketClient
import com.example.myjarvice.data.PendingEmail
import com.example.myjarvice.data.OnDeviceInferenceEngine
import com.example.myjarvice.data.SettingsStore
import com.example.myjarvice.data.SpeechManager
import com.example.myjarvice.data.SmartMode
import com.example.myjarvice.data.LocalKnowledgeStore
import com.example.myjarvice.data.LocalCalculator
import com.example.myjarvice.data.PhotoAttachment
import com.example.myjarvice.data.VoiceOption
import com.example.myjarvice.wake.WakeEvents
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

class MainScreenViewModel(application: Application) : AndroidViewModel(application) {

    val wsClient = JarvisWebSocketClient()
    val deviceContext = DeviceContextProvider(application.applicationContext)
    val speechManager = SpeechManager(application.applicationContext)
    val neuralAudioPlayer = NeuralAudioPlayer(application.applicationContext)
    private val actionExecutor = DeviceActionExecutor(application.applicationContext)
    private val historyStore = ChatHistoryStore(application.applicationContext)
    private val onDeviceEngine = OnDeviceInferenceEngine(application.applicationContext)
    private val knowledgeStore = LocalKnowledgeStore(application.applicationContext)
    private var localRequestActive = false

    val connectionStatus: StateFlow<ConnectionStatus> = wsClient.connectionStatus
    val chatHistory: StateFlow<List<JarvisMessage>> = wsClient.chatHistory

    val isSpeaking: StateFlow<Boolean> = combine(speechManager.isSpeaking, neuralAudioPlayer.isSpeaking) { s1, s2 -> s1 || s2 }
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    val isListening: StateFlow<Boolean> = speechManager.isListening



    private val settings = SettingsStore(application.applicationContext)

    // Restored from disk so the link comes back by itself on every launch.
    private val _serverIp = MutableStateFlow(settings.serverIp)
    val serverIp: StateFlow<String> = _serverIp.asStateFlow()
    private val _serverToken = MutableStateFlow(settings.serverToken)
    val serverToken: StateFlow<String> = _serverToken.asStateFlow()

    /** Saved historical chat sessions for ChatGPT-style sidebar */
    private val _savedSessions = MutableStateFlow<List<ChatSession>>(emptyList())
    val savedSessions: StateFlow<List<ChatSession>> = _savedSessions.asStateFlow()

    private var currentSessionId: String = UUID.randomUUID().toString()
    private var sessionCreatedAt: Long = System.currentTimeMillis()

    /** Full-screen, hands-free voice mode (the ChatGPT-style orb screen). */
    private val _voiceModeActive = MutableStateFlow(false)
    val voiceModeActive: StateFlow<Boolean> = _voiceModeActive.asStateFlow()

    /** While muted, voice mode stays open but the recogniser is not restarted. */
    private val _micMuted = MutableStateFlow(false)
    val micMuted: StateFlow<Boolean> = _micMuted.asStateFlow()

    /** True between sending a query and the reply landing, so the UI can show progress. */
    private val _isThinking = MutableStateFlow(false)
    val isThinking: StateFlow<Boolean> = _isThinking.asStateFlow()
    private val _responseRoute = MutableStateFlow("Choose Fast for private local answers; Strong uses your PC/server.")
    val responseRoute: StateFlow<String> = _responseRoute.asStateFlow()

    /** Non-null while an email draft is waiting on the user's yes/no. */
    val pendingEmail: StateFlow<PendingEmail?> = wsClient.pendingEmail

    /** Phone commands require a local confirmation before execution. */
    private val _pendingAction = MutableStateFlow<JarvisAction?>(null)
    val pendingAction: StateFlow<JarvisAction?> = _pendingAction.asStateFlow()


    val micLevel: StateFlow<Float> = combine(speechManager.micLevel, neuralAudioPlayer.audioLevel) { m, n ->
        if (n > 0.05f) n else m
    }.stateIn(viewModelScope, SharingStarted.Eagerly, 0f)

    val voices: StateFlow<List<VoiceOption>> = speechManager.voices
    val selectedVoiceId: StateFlow<String> = speechManager.selectedVoiceId

    init {
        // Load existing session history into sidebar, but start with a clean new session on start
        _savedSessions.value = historyStore.loadAllSessions()

        if (_serverIp.value.isNotBlank() && _serverToken.value.isNotBlank()) {
            wsClient.connect(_serverIp.value, _serverToken.value)
        }

        // Persist messages whenever chat history changes
        viewModelScope.launch {
            wsClient.chatHistory.collect { messages ->
                if (messages.isNotEmpty()) {
                    val userMsg = messages.firstOrNull { it.sender == "USER" }
                    val rawTitle = userMsg?.text ?: "Conversation"
                    val cleanTitle = if (rawTitle.length > 38) rawTitle.take(38) + "..." else rawTitle

                    historyStore.saveSession(
                        ChatSession(
                            id = currentSessionId,
                            title = cleanTitle,
                            createdAt = sessionCreatedAt,
                            updatedAt = System.currentTimeMillis(),
                            messages = messages
                        )
                    )
                    _savedSessions.value = historyStore.loadAllSessions()
                }
            }
        }

        viewModelScope.launch {
            wsClient.latestResponse.collect { msg ->
                msg?.let {
                    if (it.sender != "USER" && !localRequestActive) _isThinking.value = false
                    if (it.sender.startsWith("JARVIS", ignoreCase = true)) {
                        if (!it.audioB64.isNullOrBlank()) {
                            speechManager.stopSpeaking()
                            neuralAudioPlayer.playBase64Audio(it.audioB64)
                        } else {
                            neuralAudioPlayer.stop()
                            speechManager.speak(it.text)
                        }
                    }
                }
            }
        }

        // Execute phone actions (call / open app) the server directs.
        viewModelScope.launch {
            wsClient.latestAction.collect { action ->
                action?.let {
                    if (it.type.equals("CALL", ignoreCase = true)) {
                        _pendingAction.value = it
                    } else {
                        // Safe actions (open app, camera, maps, flashlight, alarm, whatsapp) execute immediately
                        actionExecutor.execute(it)
                        viewModelScope.launch { JarvisSoundFx.playSuccessChime() }
                    }
                }
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
            isSpeaking.collect { speaking ->
                if (!speaking && _voiceModeActive.value && !_micMuted.value &&
                    !isListening.value && !_isThinking.value
                ) {
                    beginListening()
                }
            }
        }
    }

    fun startNewChat() {
        currentSessionId = UUID.randomUUID().toString()
        sessionCreatedAt = System.currentTimeMillis()
        wsClient.clearChat()
        _savedSessions.value = historyStore.loadAllSessions()
    }

    fun loadSession(session: ChatSession) {
        currentSessionId = session.id
        sessionCreatedAt = session.createdAt
        wsClient.setChatHistory(session.messages)
    }

    fun deleteSession(sessionId: String) {
        historyStore.deleteSession(sessionId)
        _savedSessions.value = historyStore.loadAllSessions()
        if (currentSessionId == sessionId) {
            startNewChat()
        }
    }

    fun clearAllHistory() {
        historyStore.clearAll()
        _savedSessions.value = emptyList()
        startNewChat()
    }

    /** Single entry point for listening, so every path re-arms the same way. */
    private fun beginListening() {
        speechManager.startListening(
            onResult = { voiceText -> sendQuery(voiceText) },
            onNoResult = {
                viewModelScope.launch {
                    delay(RELISTEN_DELAY_MS)
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
        viewModelScope.launch { JarvisSoundFx.playWakeChime() }
        if (!isListening.value && !isSpeaking.value) {
            beginListening()
        }
    }

    fun exitVoiceMode() {
        _voiceModeActive.value = false
        _isThinking.value = false
        neuralAudioPlayer.stop()
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

    fun resolvePendingAction(approved: Boolean) {
        val action = _pendingAction.value ?: return
        _pendingAction.value = null
        if (approved) {
            actionExecutor.execute(action)
            viewModelScope.launch { JarvisSoundFx.playSuccessChime() }
        }
    }

    /** Plain-text transcript for voice mode's share action. */
    fun buildTranscript(): String =
        chatHistory.value.joinToString("\n\n") { msg ->
            val who = if (msg.sender == "USER") "You" else "Jarvis"
            "$who: ${msg.text}"
        }

    fun updateServerConnection(newIp: String, newToken: String) {
        val trimmed = newIp.trim()
        _serverIp.value = trimmed
        _serverToken.value = newToken.trim()
        settings.serverIp = trimmed
        settings.serverToken = _serverToken.value
        wsClient.updateServerConnection(trimmed, _serverToken.value)
    }

    fun sendQuery(text: String, photo: PhotoAttachment? = null) {
        if (text.isBlank() || localRequestActive) return

        // Explicit local tools never forward saved facts or document excerpts to a host.
        val command = text.trim()
        if (command.startsWith("calculate ", true) || command.startsWith("remember: ", true) ||
            command.equals("show memories", true) || command.startsWith("search documents:", true)) {
            localRequestActive = true
            _responseRoute.value = "Local tool · stays on this phone"
            _isThinking.value = true
            wsClient.addLocalMessage(JarvisMessage(sender = "USER", text = text, type = "QUERY", timestamp = timestampNow()))
            viewModelScope.launch {
                try {
                    val reply = withContext(Dispatchers.IO) {
                        runCatching {
                            when {
                                command.startsWith("calculate ", true) -> LocalCalculator.evaluate(command.substringAfter(' '))
                                command.startsWith("remember: ", true) -> {
                                    knowledgeStore.remember(command.substringAfter(':'))
                                    "Saved on this phone. Review or delete it in Settings → Local memory & documents."
                                }
                                command.equals("show memories", true) -> knowledgeStore.entries().filter { it.memory }
                                    .joinToString("\n") { "• ${it.text}" }.ifBlank { "No saved memories yet." }
                                else -> LocalKnowledgeStore.rank(command.substringAfter(':'), knowledgeStore.entries().filterNot { it.memory })
                                    .mapIndexed { i, hit -> "[${i + 1}] ${hit.source}\n${hit.text}" }
                                    .joinToString("\n\n").ifBlank { "No matching passages. Import a document in Settings or try more specific keywords." }
                            }
                        }
                    }
                    wsClient.addLocalMessage(JarvisMessage(sender = "JARVIS (Local tool)",
                        text = reply.getOrElse { it.message ?: "Local tool failed." },
                        type = if (reply.isSuccess) "RESPONSE" else "ERROR", timestamp = timestampNow()))
                } finally { localRequestActive = false; _isThinking.value = false }
            }
            return
        }

        _isThinking.value = true
        val useOnDevice = when (settings.smartMode) {
            SmartMode.FAST_ON_DEVICE -> true
            SmartMode.STRONG_HOST -> false
            // Prefer the stronger host when it is reachable; retain a private,
            // offline path whenever the host is unavailable.
            SmartMode.AUTO -> connectionStatus.value != ConnectionStatus.CONNECTED && hasOnDeviceModel()
        }
        if (!useOnDevice) {
            _responseRoute.value = if (photo == null) {
                "PC/server · sending this request to your configured host"
            } else {
                "PC/server · analysing your photo on the configured host"
            }
            val ctx = deviceContext.getDeviceContext()
            wsClient.sendMessage(
                query = text,
                voiceId = selectedVoiceId.value,
                deviceContext = ctx,
                imageBase64 = photo?.base64,
                imageMimeType = photo?.mimeType,
                imageOcrText = photo?.ocrText
            )
            return
        }

        val localPrompt = if (photo?.hasReadableText == true) {
            "Photo text (read privately on this phone):\n${photo.ocrText}\n\nUser question: $text"
        } else {
            text
        }
        val userMessage = JarvisMessage(
            sender = "USER",
            text = text,
            type = "QUERY",
            timestamp = timestampNow(),
            image = photo?.dataUrl
        )
        localRequestActive = true
        _responseRoute.value = if (photo == null) {
            "On-device · loading / generating locally"
        } else if (photo.hasReadableText) {
            "On-device · reading the photo text privately"
        } else {
            "On-device · photo has no readable text; Strong mode can analyse the full image"
        }
        wsClient.addLocalMessage(userMessage)
        viewModelScope.launch {
            try {
            val result = withContext(Dispatchers.Default) {
                onDeviceEngine.generate(
                    modelPath = settings.onDeviceModelPath,
                    query = localPrompt,
                    chatHistory = chatHistory.value.dropLast(1),
                    personality = settings.aiPersonality,
                    temperature = settings.temperature
                )
            }
            _isThinking.value = false
            result.fold(
                onSuccess = { reply ->
                    _responseRoute.value = "On-device · response completed locally"
                    wsClient.addLocalMessage(
                        JarvisMessage(
                            sender = "JARVIS (On-device)",
                            text = reply,
                            timestamp = timestampNow()
                        )
                    )
                },
                onFailure = { error ->
                    _responseRoute.value = "On-device · failed; nothing forwarded to the server"
                    wsClient.addLocalMessage(
                        JarvisMessage(
                            sender = "JARVIS (On-device)",
                            text = error.message ?: "On-device inference failed.",
                            type = "ERROR",
                            timestamp = timestampNow()
                        )
                    )
                }
            )
            } finally { localRequestActive = false; _isThinking.value = false }
        }
    }

    fun clearChat() {
        startNewChat()
    }

    fun speak(text: String) {
        speechManager.speak(text)
    }

    fun stopSpeaking() {
        speechManager.stopSpeaking()
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
        onDeviceEngine.close()
    }

    private companion object {
        const val RELISTEN_DELAY_MS = 700L

        fun timestampNow(): String = java.text.SimpleDateFormat(
            "yyyy-MM-dd'T'HH:mm:ss",
            java.util.Locale.getDefault()
        ).format(java.util.Date())
    }

    private fun hasOnDeviceModel(): Boolean =
        File(settings.onDeviceModelPath).isFile ||
            File(getApplication<Application>().filesDir, "models/jarvis-on-device.litertlm").isFile
}
