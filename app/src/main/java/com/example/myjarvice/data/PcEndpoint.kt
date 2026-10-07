package com.example.myjarvice.data

import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/** One parsing contract for chat, Activity and diagnostics; never embeds credentials. */
object PcEndpoint {
    fun base(address: String): HttpUrl {
        val clean = address.trim()
        require(clean.isNotBlank() && clean.length <= 2048 && clean.none { it.isWhitespace() || it.code < 32 } && '\\' !in clean) {
            "Enter a valid PC address."
        }
        val explicit = clean.contains("://")
        val url = when {
            clean.startsWith("wss://", true) -> "https://" + clean.substring(6)
            clean.startsWith("ws://", true) -> "http://" + clean.substring(5)
            explicit -> clean
            else -> "http://$clean"
        }.toHttpUrlOrNull() ?: error("Enter a valid PC address.")
        require(url.username.isEmpty() && url.password.isEmpty() && url.query == null && url.fragment == null) {
            "Use an address without credentials, query or fragment."
        }
        require(url.port in 1..65535) { "Enter a valid PC port." }
        // Bare hostname/IP defaults to Jarvis's port, not HTTP port 80.
        val authority = clean.substringBefore('/')
        val hasPort = if (authority.startsWith("[")) authority.substringAfter(']', "").startsWith(":") else ':' in authority
        return if (!explicit && !hasPort) url.newBuilder().port(8000).build() else url
    }

    fun api(address: String, path: String): HttpUrl {
        require(path in setOf("/api/health", "/api/audit/recent"))
        return base(address).newBuilder().encodedPath(path).build()
    }

    fun websocket(address: String): String {
        val url = base(address)
        val path = if (url.encodedPath == "/") "/ws/jarvis" else url.encodedPath
        val http = url.newBuilder().encodedPath(path).build().toString()
        return if (url.isHttps) "wss://" + http.substring(8) else "ws://" + http.substring(7)
    }

    fun validToken(token: String): Boolean = token.trim().let { value ->
        value.length in 1..512 && value.all { it.code in 33..126 }
    }
}
