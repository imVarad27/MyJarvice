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

data class JarviceMessage(
    val sender: String,
    val text: String,
    val type: String = "RESPONSE",
    val timestamp: String = ""
)

/** An email Jarvis has drafted and is holding until the user approves it. */
data class PendingEmail(
    val id: String,
    val to: String,
    val subject: String,
    val body: String
)

/** A directive from the server for the phone to execute locally (Phase 3). */
data class JarviceAction(
    val id: String,      // unique per message (server timestamp) so repeats re-trigger
    val type: String,    // "CALL" | "OPEN_APP"
    val query: String
)

class JarviceWebSocketClient {

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

    private val _latestResponse = MutableStateFlow<JarviceMessage?>(null)
    val latestResponse: StateFlow<JarviceMessage?> = _latestResponse

    private val _latestAction = MutableStateFlow<JarviceAction?>(null)
    val latestAction: StateFlow<JarviceAction?> = _latestAction

    private val _chatHistory = MutableStateFlow<List<JarviceMessage>>(emptyList())
    val chatHistory: StateFlow<List<JarviceMessage>> = _chatHistory

    private val _pendingEmail = MutableStateFlow<PendingEmail?>(null)
    val pendingEmail: StateFlow<PendingEmail?> = _pendingEmail

    private var serverIp = ""
    private var serverToken = ""
    private val fallbackIps = listOf("127.0.0.1:8000", "192.168.1.37:8000", "192.168.1.34:8000", "192.168.137.1:8000")


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
            serverIp.contains(":") -> "ws://$serverIp/ws/jarvice"
            else -> "ws://$serverIp:8000/ws/jarvice"
        }

        Log.d("JarviceWS", "Attempting connection to: $wsUrl")
        val request = Request.Builder().url(wsUrl)
            .header("Authorization", "Bearer $serverToken")
            .build()

        webSocket?.cancel()
        webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                Log.d("JarviceWS", "WebSocket Connected Successfully to $wsUrl!")
                retryAttempt = 0
                _connectionStatus.value = ConnectionStatus.CONNECTED
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                Log.d("JarviceWS", "Message received: $text")
                try {
                    val json = JSONObject(text)
                    val sender = json.optString("sender", "JARVIS")
                    val messageText = json.optString("text", "")
                    val msgType = json.optString("type", "RESPONSE")
                    val ts = json.optString("timestamp", "")

                    val msg = JarviceMessage(sender, messageText, msgType, ts)
                    _latestResponse.value = msg
                    _chatHistory.value = _chatHistory.value + msg

                    // A drafted email is held here until the user approves it; the
                    // server will not send anything without an explicit verdict.
                    json.optJSONObject("pending_email")?.let { draft ->
                        _pendingEmail.value = PendingEmail(
                            id = draft.optString("id"),
                            to = draft.optString("to"),
                            subject = draft.optString("subject"),
                            body = draft.optString("body")
                        )
                    }

                    // Phase 3: execute a phone action if the server sent one.
                    val actionObj = json.optJSONObject("action")
                    if (actionObj != null) {
                        _latestAction.value = JarviceAction(
                            id = ts.ifBlank { System.currentTimeMillis().toString() },
                            type = actionObj.optString("type"),
                            query = actionObj.optString("query")
                        )
                    }
                } catch (e: Exception) {
                    Log.e("JarviceWS", "Error parsing message: ${e.message}")
                }
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                Log.e("JarviceWS", "WebSocket Connection Failed to $wsUrl: ${t.message}")
                _connectionStatus.value = ConnectionStatus.ERROR
                scheduleReconnect()
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                Log.d("JarviceWS", "WebSocket Closed: $reason")
                _connectionStatus.value = ConnectionStatus.DISCONNECTED
                scheduleReconnect()
            }
        })
    }


    fun sendMessage(userText: String, phoneContext: Map<String, Any> = emptyMap()) {
        val userMsg = JarviceMessage("USER", userText)
        _chatHistory.value = _chatHistory.value + userMsg

        val payload = JSONObject().apply {
            put("text", userText)
            put("context", JSONObject(phoneContext))
        }

        if (_connectionStatus.value == ConnectionStatus.CONNECTED) {
            webSocket?.send(payload.toString())
        } else {
            val offlineMsg = JarviceMessage(
                sender = "Jarvis (Offline)",
                text = "Sir, server link ($serverIp) is offline. Re-establishing link..."
            )
            _latestResponse.value = offlineMsg
            _chatHistory.value = _chatHistory.value + offlineMsg
            connect(serverIp, serverToken)
        }
    }

    /** Relays the user's verdict on a drafted email; clears it either way. */
    fun resolvePendingEmail(id: String, approved: Boolean) {
        _pendingEmail.value = null
        val payload = JSONObject().apply {
            put("approve_email", JSONObject().apply {
                put("id", id)
                put("approved", approved)
            })
        }
        webSocket?.send(payload.toString())
    }

    fun updateServerConnection(ip: String, pairingToken: String) {
        retryAttempt = 0
        connect(ip, pairingToken)
    }

    /**
     * Keeps the link up on its own: the server restarting, Wi-Fi dropping, or the USB
     * tunnel being re-established all resolve without the user touching anything.
     * Backs off 1s → 30s so a genuinely absent host doesn't spin the radio.
     */
    private fun scheduleReconnect() {
        if (!keepConnected) return
        if (reconnectJob?.isActive == true) return

        reconnectJob = scope.launch {
            val delayMs = (INITIAL_RETRY_MS * (1L shl retryAttempt.coerceAtMost(5)))
                .coerceAtMost(MAX_RETRY_MS)
            retryAttempt++
            delay(delayMs)
            if (keepConnected && _connectionStatus.value != ConnectionStatus.CONNECTED) {
                Log.d("JarviceWS", "Reconnecting to configured server (attempt $retryAttempt)")
                connect(serverIp, serverToken)
            }
        }
    }

    fun clearChat() {
        _chatHistory.value = emptyList()
        _latestResponse.value = null
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
