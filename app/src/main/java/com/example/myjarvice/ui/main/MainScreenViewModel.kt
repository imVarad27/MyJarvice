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
import com.example.myjarvice.data.SafePhoneAction
import com.example.myjarvice.data.PhoneActionPolicy
import com.example.myjarvice.data.PhotoAttachment
import com.example.myjarvice.data.VoiceOption
import com.example.myjarvice.wake.WakeEvents
import com.example.myjarvice.wake.VoiceActionPolicy
import kotlinx.coroutines.delay
import kotlinx.coroutines.Job
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.flow.first
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
    private val microphoneOwner = UUID.randomUUID().toString()
    private var popupSession = false
    fun useAsPopup() { popupSession = true }

    val wsClient = JarvisWebSocketClient()
    val deviceContext = DeviceContextProvider(application.applicationContext)
    val speechManager = SpeechManager(application.applicationContext)
    val neuralAudioPlayer = NeuralAudioPlayer(application.applicationContext)
    private val actionExecutor = DeviceActionExecutor(application.applicationContext)
    private val historyStore = ChatHistoryStore(application.applicationContext)
    private val onDeviceEngine = OnDeviceInferenceEngine(application.applicationContext)
    private var localRequestActive = false
    private var hostActionsAllowed = true
    private var listeningJob: Job? = null
    private val preparingMic = MutableStateFlow(false)

    val connectionStatus: StateFlow<ConnectionStatus> = wsClient.connectionStatus
    val chatHistory: StateFlow<List<JarvisMessage>> = wsClient.chatHistory

    val isSpeaking: StateFlow<Boolean> = combine(speechManager.isSpeaking, neuralAudioPlayer.isSpeaking) { s1, s2 -> s1 || s2 }
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    val isListening: StateFlow<Boolean> = speechManager.isListening
    val liveTranscript = speechManager.recognizedText
    val recognitionStatus = speechManager.recognitionStatus



    private val settings = SettingsStore(application.applicationContext)
    private val _assistantPaused = MutableStateFlow(settings.assistantPaused)
    val assistantPaused = _assistantPaused.asStateFlow()
    private val _smartMode = MutableStateFlow(settings.smartMode)
    val smartMode = _smartMode.asStateFlow()
    fun selectSmartMode(mode: SmartMode) {
        if (_isThinking.value) return
        settings.smartMode = mode
        _smartMode.value = mode
        refreshPreferences()
    }

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
    private val _voiceOwnerVerified = MutableStateFlow(false)
    val voiceOwnerVerified: StateFlow<Boolean> = _voiceOwnerVerified.asStateFlow()

    /** While muted, voice mode stays open but the recogniser is not restarted. */
    private val _micMuted = MutableStateFlow(false)
    val micMuted: StateFlow<Boolean> = _micMuted.asStateFlow()

    /** True between sending a query and the reply landing, so the UI can show progress. */
    private val _isThinking = MutableStateFlow(false)
    val isThinking: StateFlow<Boolean> = _isThinking.asStateFlow()
    private val _responseRoute = MutableStateFlow("Ready when you are")
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
        viewModelScope.launch {
            settings.observeAssistantPaused().collect { paused ->
                _assistantPaused.value = paused
                if (paused) {
                    val requestInProgress = _isThinking.value
                    exitVoiceMode()
                    _isThinking.value = requestInProgress
                    _pendingAction.value = null
                    _responseRoute.value = "Jarvis is paused · resume it in Settings"
                } else refreshPreferences()
            }
        }
        viewModelScope.launch {
            combine(isSpeaking, isListening, _voiceModeActive, _isThinking, preparingMic) { speaking, listening, voice, thinking, preparing ->
                speaking || listening || voice || thinking || preparing
            }.collect { WakeEvents.setMicrophoneBusy(microphoneOwner, it) }
        }
        // Load existing session history into sidebar, but start with a clean new session on start
        _savedSessions.value = historyStore.loadAllSessions()

        if (_serverIp.value.isNotBlank() && _serverToken.value.isNotBlank()) {
            wsClient.connect(_serverIp.value, _serverToken.value)
        }

        // Persist messages whenever chat history changes
        viewModelScope.launch {
            wsClient.chatHistory.collect { messages ->
                if (messages.isNotEmpty() && messages.none { it.type == "PARTIAL" }) {
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
                    if (!settings.assistantPaused && it.sender.startsWith("JARVIS", ignoreCase = true) &&
                        (!WakeEvents.popupVisible.value || popupSession) &&
                        (settings.autoSpeakReplies || _voiceModeActive.value)) {
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
                if (settings.assistantPaused) return@collect
                action?.let {
                    val validated = PhoneActionPolicy.validate(it.type, it.query)
                    if (validated == null) {
                        wsClient.addLocalMessage(JarvisMessage(
                            sender = "JARVIS (Safety)", text = "The proposed phone action was invalid, so nothing ran.",
                            type = "ERROR", timestamp = timestampNow()
                        ))
                    } else if (!hostActionsAllowed) {
                        wsClient.addLocalMessage(JarvisMessage(
                            sender = "JARVIS (Voice protection)", text = "This voice session wasn't verified, so the proposed phone action was blocked.",
                            type = "ERROR", timestamp = timestampNow()
                        ))
                    } else if (validated.requiresConfirmation) {
                        _pendingAction.value = it.copy(type = validated.type, query = validated.query)
                    } else {
                        reportPhoneResult(actionExecutor.executeLocalSafe(validated))
                    }
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

    fun openSessionById(id: String) {
        historyStore.loadAllSessions().firstOrNull { it.id == id }?.let(::loadSession)
    }

    fun saveForHandoff(): String? {
        val messages = chatHistory.value
        if (messages.isEmpty() || _isThinking.value) return null
        historyStore.saveSession(ChatSession(
            id = currentSessionId,
            title = messages.firstOrNull { it.sender == "USER" }?.text?.take(38) ?: "Conversation",
            createdAt = sessionCreatedAt, updatedAt = System.currentTimeMillis(), messages = messages
        ))
        return currentSessionId
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
        if (settings.assistantPaused) return
        if (listeningJob?.isActive == true || isListening.value) return
        preparingMic.value = true
        WakeEvents.setMicrophoneBusy(microphoneOwner, true)
        listeningJob = viewModelScope.launch {
            try {
                if (withTimeoutOrNull(1500) { WakeEvents.captureReleased.first { it } } != true) return@launch
                if (settings.assistantPaused) return@launch
                speechManager.startListening(
                    onReady = { viewModelScope.launch { JarvisSoundFx.playWakeChime() } },
                    onResult = { voiceText -> sendQuery(voiceText, fromVoice = true) },
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
            } finally { preparingMic.value = false }
        }
    }

    fun enterVoiceMode(verifiedByWake: Boolean = false, matchedOwner: Boolean = WakeEvents.ownerVerified.value) {
        if (settings.assistantPaused) {
            _responseRoute.value = "Jarvis is paused · resume it in Settings"
            return
        }
        _voiceOwnerVerified.value = verifiedByWake && matchedOwner
        _voiceModeActive.value = true
        _micMuted.value = false
        if (!isListening.value && !isSpeaking.value) {
            beginListening()
        }
    }

    fun exitVoiceMode() {
        listeningJob?.cancel()
        preparingMic.value = false
        _voiceModeActive.value = false
        _voiceOwnerVerified.value = false
        WakeEvents.ownerVerified.value = false
        _isThinking.value = false
        neuralAudioPlayer.stop()
        speechManager.stopListening()
        speechManager.stopSpeaking()
    }

    fun toggleMute() {
        val muted = !_micMuted.value
        _micMuted.value = muted
        if (muted) {
            listeningJob?.cancel()
            preparingMic.value = false
            speechManager.stopListening()
        } else if (!isSpeaking.value) {
            beginListening()
        }
    }

    /** Editing is explicit user input; release dictation without closing the popup. */
    fun pauseVoiceForTyping() {
        _micMuted.value = true
        listeningJob?.cancel()
        preparingMic.value = false
        speechManager.stopListening()
        stopSpeaking()
    }

    fun selectVoice(voiceId: String) = speechManager.applyVoice(voiceId)

    /** Nothing leaves the host server until this is called with approved = true. */
    fun resolvePendingEmail(id: String, approved: Boolean) {
        if (settings.assistantPaused && approved) {
            wsClient.resolvePendingEmail(id, false)
            wsClient.addLocalMessage(JarvisMessage(
                sender = "JARVIS (Safety)",
                text = "Jarvis is paused, so I discarded that email approval. Resume Jarvis before drafting or approving another action.",
                type = "ERROR",
                timestamp = timestampNow()
            ))
            return
        }
        wsClient.resolvePendingEmail(id, approved)
    }

    fun resolvePendingAction(approved: Boolean) {
        val action = _pendingAction.value ?: return
        _pendingAction.value = null
        if (settings.assistantPaused && approved) {
            wsClient.addLocalMessage(JarvisMessage(
                sender = "JARVIS (Safety)",
                text = "Jarvis is paused, so I did not run that phone action.",
                type = "ERROR",
                timestamp = timestampNow()
            ))
            return
        }
        if (approved) {
            reportPhoneResult(actionExecutor.execute(action, approved = true))
        }
    }

    private fun reportPhoneResult(result: Result<String>) {
        result.onSuccess { text ->
            wsClient.addLocalMessage(JarvisMessage(sender = "JARVIS (Phone)", text = text, timestamp = timestampNow()))
            viewModelScope.launch { JarvisSoundFx.playSuccessChime() }
        }.onFailure { error ->
            wsClient.addLocalMessage(JarvisMessage(sender = "JARVIS (Phone)",
                text = error.message ?: "Phone action failed.", type = "ERROR", timestamp = timestampNow()))
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

    fun refreshPreferences() {
        _smartMode.value = settings.smartMode
        if (settings.serverIp != _serverIp.value || settings.serverToken != _serverToken.value) {
            if (settings.serverIp.isNotBlank()) updateServerConnection(settings.serverIp, settings.serverToken)
            else {
                wsClient.disconnect()
                _serverIp.value = ""
                _serverToken.value = settings.serverToken
            }
        }
        if (settings.assistantPaused) _responseRoute.value = "Jarvis is paused · resume it in Settings"
        else if (!_isThinking.value) _responseRoute.value = when (settings.smartMode) {
            SmartMode.FAST_ON_DEVICE -> "On this phone · private"
            SmartMode.STRONG_HOST -> "Connected PC"
            SmartMode.AUTO -> "Automatic · chooses an available model"
        }
    }

    fun sendQuery(text: String, photo: PhotoAttachment? = null, fromVoice: Boolean = false) {
        if (text.isBlank() || localRequestActive || _isThinking.value) return
        if (settings.assistantPaused) {
            _responseRoute.value = "Jarvis is paused · resume it in Settings"
            wsClient.addLocalMessage(JarvisMessage(
                sender = "JARVIS (Safety)",
                text = "Jarvis is paused. Resume it in Settings before starting a new request.",
                type = "ERROR",
                timestamp = timestampNow()
            ))
            return
        }
        if (com.example.myjarvice.data.LocalBenchmarkRuntime.active.value) {
            _responseRoute.value = "Phone model test is running"
            return
        }

        val profileEnabled = settings.voiceMatchEnabled && settings.isVoiceProfileEnrolled
        val allowLocalActions = photo == null && !VoiceActionPolicy.shouldBlock(
            changesState = true, voiceMode = fromVoice,
            profileEnabled = profileEnabled, ownerVerified = _voiceOwnerVerified.value
        )

        _isThinking.value = true
        val useOnDevice = when (settings.smartMode) {
            SmartMode.FAST_ON_DEVICE -> true
            SmartMode.STRONG_HOST -> false
            // Prefer the stronger host when it is reachable; retain a private,
            // offline path whenever the host is unavailable.
            SmartMode.AUTO -> connectionStatus.value != ConnectionStatus.CONNECTED && hasOnDeviceModel()
        }
        if (!useOnDevice) {
            hostActionsAllowed = allowLocalActions
            _responseRoute.value = if (photo == null) {
                "Using your connected PC"
            } else {
                "Reading the photo with your connected PC"
            }
            val ctx = deviceContext.getDeviceContext()
            wsClient.sendMessage(
                query = text,
                voiceId = selectedVoiceId.value,
                deviceContext = ctx,
                imageBase64 = photo?.base64,
                imageMimeType = photo?.mimeType,
                imageOcrText = photo?.ocrText,
                voiceMode = fromVoice,
                speakResponse = settings.autoSpeakReplies || _voiceModeActive.value,
                voiceProfileEnabled = profileEnabled,
                speakerVerified = _voiceOwnerVerified.value
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
            "Starting the phone model"
        } else if (photo.hasReadableText) {
            "Reading the photo on this phone"
        } else {
            "No readable text found in the photo"
        }
        wsClient.addLocalMessage(userMessage)
        if (photo != null && !photo.hasReadableText) {
            wsClient.addLocalMessage(JarvisMessage(sender = "JARVIS (On-device)",
                text = "I couldn't find readable text in this photo. Try a clearer picture of the page, or choose Connected PC if your PC model supports images.",
                type = "ERROR", timestamp = timestampNow()))
            localRequestActive = false
            _isThinking.value = false
            return
        }
        viewModelScope.launch {
            try {
            val result = withContext(Dispatchers.Default) {
                onDeviceEngine.generate(
                    modelPath = settings.onDeviceModelPath,
                    query = localPrompt,
                    chatHistory = chatHistory.value.dropLast(1),
                    personality = settings.aiPersonality,
                    temperature = settings.temperature,
                    onStage = { _responseRoute.value = it },
                    allowActions = allowLocalActions
                )
            }
            _isThinking.value = false
            result.fold(
                onSuccess = { reply ->
                    _responseRoute.value = "Answered on this phone"
                    wsClient.addLocalMessage(
                        JarvisMessage(
                            sender = "JARVIS (On-device)",
                            text = reply,
                            timestamp = timestampNow()
                        )
                    )
                },
                onFailure = { error ->
                    _responseRoute.value = "Phone model couldn't finish · nothing was shared"
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
        if (settings.assistantPaused) return
        speechManager.speak(text)
    }

    fun stopSpeaking() {
        speechManager.stopSpeaking()
        neuralAudioPlayer.stop()
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
        neuralAudioPlayer.stop()
        WakeEvents.setMicrophoneBusy(microphoneOwner, false)
        onDeviceEngine.close()
    }

    private companion object {
        const val RELISTEN_DELAY_MS = 1500L

        fun timestampNow(): String = java.text.SimpleDateFormat(
            "yyyy-MM-dd'T'HH:mm:ss",
            java.util.Locale.getDefault()
        ).format(java.util.Date())
    }

    fun hasOnDeviceModel(): Boolean =
        File(settings.onDeviceModelPath).isFile ||
            File(getApplication<Application>().filesDir, "models/jarvis-on-device.litertlm").isFile
}
