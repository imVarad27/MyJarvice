package com.example.myjarvice.data

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONObject
import java.util.concurrent.TimeUnit

enum class ConnectionStatus {
    DISCONNECTED,
    CONNECTING,
    CONNECTED,
    ERROR
}

data class JarvisMessage(
    val sender: String,
    val text: String,
    val type: String = "RESPONSE",
    val timestamp: String = "",
    val image: String? = null
)


/** An email Jarvis has drafted and is holding until the user approves it. */
data class PendingEmail(
    val id: String,
    val to: String,
    val subject: String,
    val body: String
)

/** A directive from the server for the phone to execute locally. */
data class JarvisAction(
    val id: String,      // unique per message (server timestamp) so repeats re-trigger
    val type: String,    // "CALL" | "OPEN_APP"
    val query: String
)

// Backward-compatibility aliases
typealias JarviceMessage = JarvisMessage
typealias JarviceAction = JarvisAction

class JarvisWebSocketClient {

    private var client: OkHttpClient = OkHttpClient.Builder()
        .readTimeout(0, TimeUnit.MILLISECONDS)   // 0 = no read timeout; pings police liveness
        .connectTimeout(5, TimeUnit.SECONDS)
        .pingInterval(20, TimeUnit.SECONDS)      // detects a silently dead link
        .build()

    private var webSocket: WebSocket? = null

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var reconnectJob: Job? = null

    /** False only after an explicit disconnect(), so teardown doesn't fight the retry loop. */
    private var keepConnected = true
    private var retryAttempt = 0

    private val _connectionStatus = MutableStateFlow(ConnectionStatus.DISCONNECTED)
    val connectionStatus: StateFlow<ConnectionStatus> = _connectionStatus

    private val _latestResponse = MutableStateFlow<JarvisMessage?>(null)
    val latestResponse: StateFlow<JarvisMessage?> = _latestResponse

    private val _latestAction = MutableStateFlow<JarvisAction?>(null)
    val latestAction: StateFlow<JarvisAction?> = _latestAction

    private val _chatHistory = MutableStateFlow<List<JarvisMessage>>(emptyList())
    val chatHistory: StateFlow<List<JarvisMessage>> = _chatHistory

    private val _pendingEmail = MutableStateFlow<PendingEmail?>(null)
    val pendingEmail: StateFlow<PendingEmail?> = _pendingEmail

    private var serverIp = ""
    private var serverToken = ""
    private val fallbackIps = listOf("127.0.0.1:8000", "192.168.1.35:8000", "192.168.1.37:8000", "192.168.1.34:8000", "192.168.137.1:8000")


    fun connect(rawIpOrUrl: String = "", pairingToken: String = "") {
        keepConnected = true
        reconnectJob?.cancel()
        _connectionStatus.value = ConnectionStatus.CONNECTING

        val effectiveIp = if (rawIpOrUrl.isNotBlank()) rawIpOrUrl.trim() else if (serverIp.isNotBlank()) serverIp else fallbackIps[retryAttempt % fallbackIps.size]
        val effectiveToken = if (pairingToken.isNotBlank()) pairingToken.trim() else if (serverToken.isNotBlank()) serverToken else "jarvis_local_token"

        this.serverIp = effectiveIp
        this.serverToken = effectiveToken

        val wsUrl = when {
            serverIp.startsWith("ws://") || serverIp.startsWith("wss://") -> serverIp
            serverIp.contains(":") -> "ws://$serverIp/ws/jarvis"
            else -> "ws://$serverIp:8000/ws/jarvis"
        }

        Log.d("JarvisWS", "Attempting connection to: $wsUrl")
        val request = Request.Builder().url(wsUrl)
            .header("Authorization", "Bearer $serverToken")
            .build()

        webSocket?.cancel()
        webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                _connectionStatus.value = ConnectionStatus.CONNECTED
                retryAttempt = 0
                Log.d("JarvisWS", "WebSocket Connected Successfully to $wsUrl!")
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                try {
                    val obj = JSONObject(text)
                    val sender = obj.optString("sender", "JARVIS")
                    Log.d("JarvisWS", "Message received: $text")

                    val actionType = obj.optString("action", "")
                    val actionQuery = obj.optString("target", "")

                    val msgType = obj.optString("type", "RESPONSE")
                    val messageText = obj.optString("text", "")
                    val ts = obj.optString("timestamp", "")
                    val imagePayload = if (obj.has("image") && !obj.isNull("image")) obj.getString("image") else null
                    val msg = JarvisMessage(sender, messageText, msgType, ts, imagePayload)


                    // Drafted email waiting for approval
                    if (obj.has("pending_email") && !obj.isNull("pending_email")) {
                        val pe = obj.getJSONObject("pending_email")
                        _pendingEmail.value = PendingEmail(
                            id = pe.getString("id"),
                            to = pe.optString("to", ""),
                            subject = pe.optString("subject", ""),
                            body = pe.optString("body", "")
                        )
                    }


                    if (actionType.isNotBlank() && actionQuery.isNotBlank()) {
                        _latestAction.value = JarvisAction(
                            id = ts.ifBlank { System.currentTimeMillis().toString() },
                            type = actionType,
                            query = actionQuery
                        )
                    }

                    _latestResponse.value = msg
                    _chatHistory.value = _chatHistory.value + msg
                } catch (e: Exception) {
                    Log.e("JarvisWS", "Error parsing message: ${e.message}")
                }
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                _connectionStatus.value = ConnectionStatus.ERROR
                Log.e("JarvisWS", "WebSocket Connection Failed to $wsUrl: ${t.message}")

                // Auto fallback to alternative IP
                if (retryAttempt < fallbackIps.size - 1) {
                    retryAttempt++
                    val nextIp = fallbackIps[retryAttempt % fallbackIps.size]
                    Log.d("JarvisWS", "Attempting fallback IP: $nextIp")
                    connect(nextIp, serverToken)
                } else {
                    scheduleReconnect()
                }
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                _connectionStatus.value = ConnectionStatus.DISCONNECTED
                Log.d("JarvisWS", "WebSocket Closed: $reason ($code)")
                if (keepConnected) scheduleReconnect()
            }
        })
    }

    fun updateServerConnection(newIp: String, newToken: String) {
        val trimmedIp = newIp.trim()
        val trimmedToken = newToken.trim()
        if (trimmedIp != serverIp || trimmedToken != serverToken || _connectionStatus.value != ConnectionStatus.CONNECTED) {
            disconnect()
            retryAttempt = 0
            connect(trimmedIp, trimmedToken)
        }
    }

    fun sendMessage(query: String, deviceContext: Map<String, Any> = emptyMap()) {
        val userMsg = JarvisMessage(
            sender = "USER",
            text = query,
            type = "QUERY",
            timestamp = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date())
        )
        _chatHistory.value = _chatHistory.value + userMsg

        val payload = JSONObject().apply {
            put("query", query)
            put("text", query)
            put("device_context", JSONObject(deviceContext))
            put("context", JSONObject(deviceContext))
        }


        val sent = webSocket?.send(payload.toString()) ?: false
        if (!sent) {
            val offlineMsg = JarvisMessage(
                sender = "JARVIS (Offline)",
                text = "Cannot reach Host Server ($serverIp). Please ensure the Python server is running.",
                type = "ERROR"
            )
            _chatHistory.value = _chatHistory.value + offlineMsg
            _latestResponse.value = offlineMsg
        }
    }

    /**
     * Send user approval or rejection for a drafted email.
     */
    fun resolvePendingEmail(draftId: String, approved: Boolean) {
        _pendingEmail.value = null
        val payload = JSONObject().apply {
            put("approve_email", JSONObject().apply {
                put("id", draftId)
                put("approved", approved)
            })
        }
        webSocket?.send(payload.toString())
    }

    private fun scheduleReconnect() {
        if (!keepConnected) return
        if (reconnectJob?.isActive == true) return

        reconnectJob = scope.launch {
            val delayMs = (INITIAL_RETRY_MS * (1L shl retryAttempt.coerceAtMost(5)))
                .coerceAtMost(MAX_RETRY_MS)
            retryAttempt++
            delay(delayMs)
            if (keepConnected && _connectionStatus.value != ConnectionStatus.CONNECTED) {
                Log.d("JarvisWS", "Reconnecting to configured server (attempt $retryAttempt)")
                connect(serverIp, serverToken)
            }
        }
    }

    fun clearChat() {
        _chatHistory.value = emptyList()
        _latestResponse.value = null
    }

    fun setChatHistory(messages: List<JarvisMessage>) {
        _chatHistory.value = messages
    }

    fun disconnect() {
        keepConnected = false
        reconnectJob?.cancel()
        webSocket?.close(1000, "User disconnected")
        _connectionStatus.value = ConnectionStatus.DISCONNECTED
    }

    private companion object {
        const val INITIAL_RETRY_MS = 1_000L
        const val MAX_RETRY_MS = 30_000L
    }
}

// Backward-compatibility class alias
typealias JarviceWebSocketClient = JarvisWebSocketClient
