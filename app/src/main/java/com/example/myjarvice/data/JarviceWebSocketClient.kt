package com.example.myjarvice.data

import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
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

class JarviceWebSocketClient {

    private var client: OkHttpClient = OkHttpClient.Builder()
        .readTimeout(10, TimeUnit.SECONDS)
        .connectTimeout(5, TimeUnit.SECONDS)
        .build()

    private var webSocket: WebSocket? = null

    private val _connectionStatus = MutableStateFlow(ConnectionStatus.DISCONNECTED)
    val connectionStatus: StateFlow<ConnectionStatus> = _connectionStatus

    private val _latestResponse = MutableStateFlow<JarviceMessage?>(null)
    val latestResponse: StateFlow<JarviceMessage?> = _latestResponse

    private val _chatHistory = MutableStateFlow<List<JarviceMessage>>(emptyList())
    val chatHistory: StateFlow<List<JarviceMessage>> = _chatHistory

    private var serverIp = "192.168.1.35"

    fun connect(rawIpOrUrl: String = "192.168.1.35") {
        _connectionStatus.value = ConnectionStatus.CONNECTING
        this.serverIp = rawIpOrUrl.trim()

        val wsUrl = when {
            serverIp.startsWith("ws://") || serverIp.startsWith("wss://") -> serverIp
            serverIp.contains(":") -> "ws://$serverIp/ws/jarvice"
            else -> "ws://$serverIp:8000/ws/jarvice"
        }

        Log.d("JarviceWS", "Attempting connection to: $wsUrl")
        val request = Request.Builder().url(wsUrl).build()

        webSocket?.cancel()
        webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                Log.d("JarviceWS", "WebSocket Connected Successfully!")
                _connectionStatus.value = ConnectionStatus.CONNECTED
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                Log.d("JarviceWS", "Message received: $text")
                try {
                    val json = JSONObject(text)
                    val sender = json.optString("sender", "JARVICE")
                    val messageText = json.optString("text", "")
                    val msgType = json.optString("type", "RESPONSE")
                    val ts = json.optString("timestamp", "")

                    val msg = JarviceMessage(sender, messageText, msgType, ts)
                    _latestResponse.value = msg
                    _chatHistory.value = _chatHistory.value + msg
                } catch (e: Exception) {
                    Log.e("JarviceWS", "Error parsing message: ${e.message}")
                }
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                Log.e("JarviceWS", "WebSocket Connection Failed: ${t.message}", t)
                _connectionStatus.value = ConnectionStatus.ERROR
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                Log.d("JarviceWS", "WebSocket Closed: $reason")
                _connectionStatus.value = ConnectionStatus.DISCONNECTED
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
                sender = "JARVICE (Offline)",
                text = "Sir, server link ($serverIp) is offline. Re-establishing link..."
            )
            _latestResponse.value = offlineMsg
            _chatHistory.value = _chatHistory.value + offlineMsg
            connect(serverIp)
        }
    }

    fun updateServerIp(ip: String) {
        connect(ip)
    }

    fun disconnect() {
        webSocket?.close(1000, "User disconnected")
        _connectionStatus.value = ConnectionStatus.DISCONNECTED
    }
}
