package com.example.myjarvice.data

import java.net.URLDecoder
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

data class PairingProfile(val host: String, val token: String) {
    val maskedToken: String
        get() = if (token.length <= 8) "••••••••" else token.take(4) + "••••" + token.takeLast(4)

    companion object {
        private const val SCHEME_PREFIX = "jarvis-pair://v1"

        fun exportUri(host: String, token: String): String {
            val cleanHost = host.trim()
            val cleanToken = token.trim()
            require(runCatching { PcEndpoint.base(cleanHost) }.isSuccess) { "Enter a valid PC address." }
            require(PcEndpoint.validToken(cleanToken)) { "Enter a valid pairing token." }
            val encHost = URLEncoder.encode(cleanHost, StandardCharsets.UTF_8.name())
            val encToken = URLEncoder.encode(cleanToken, StandardCharsets.UTF_8.name())
            return "$SCHEME_PREFIX?host=$encHost&token=$encToken"
        }

        fun exportJson(host: String, token: String): String {
            val cleanHost = host.trim()
            val cleanToken = token.trim()
            return """{"host":"$cleanHost","token":"$cleanToken"}"""
        }

        fun parse(input: String): PairingProfile? {
            val raw = input.trim()
            if (raw.isBlank() || raw.length > 4096) return null

            // 1. Try URI scheme
            if (raw.startsWith("jarvis-pair://", ignoreCase = true) || raw.startsWith("jarvis://pair", ignoreCase = true)) {
                val query = raw.substringAfter('?', "")
                val params = query.split('&').mapNotNull { pair ->
                    val idx = pair.indexOf('=')
                    if (idx > 0) {
                        val key = runCatching { URLDecoder.decode(pair.substring(0, idx), StandardCharsets.UTF_8.name()) }.getOrDefault("")
                        val value = runCatching { URLDecoder.decode(pair.substring(idx + 1), StandardCharsets.UTF_8.name()) }.getOrDefault("")
                        key to value
                    } else null
                }.toMap()
                val host = params["host"] ?: params["address"]
                val token = params["token"] ?: params["pairing_token"]
                return validate(host, token)
            }

            // 2. Try JSON snippet
            if (raw.startsWith('{') && raw.endsWith('}')) {
                val hostMatch = Regex("""\"(?:host|address|server)\"\s*:\s*\"([^\"]+)\"""").find(raw)
                val tokenMatch = Regex("""\"(?:token|pairing_token|api_token)\"\s*:\s*\"([^\"]+)\"""").find(raw)
                val host = hostMatch?.groupValues?.get(1)
                val token = tokenMatch?.groupValues?.get(1)
                return validate(host, token)
            }

            // 3. Try key=value env lines
            if ("JARVIS_API_TOKEN" in raw || "JARVICE_API_TOKEN" in raw) {
                var host: String? = null
                var token: String? = null
                raw.lineSequence().forEach { line ->
                    val trimmed = line.trim()
                    if (trimmed.startsWith("JARVIS_API_TOKEN=") || trimmed.startsWith("JARVICE_API_TOKEN=")) {
                        token = trimmed.substringAfter('=').trim('"', '\'', ' ')
                    } else if (trimmed.startsWith("HOST=") || trimmed.startsWith("JARVIS_HOST=")) {
                        host = trimmed.substringAfter('=').trim('"', '\'', ' ')
                    }
                }
                return validate(host, token)
            }

            // 4. Try host;token=... semicolon format
            if (';' in raw && "token=" in raw) {
                val parts = raw.split(';')
                val host = parts.firstOrNull { !it.contains("token=") }?.trim()
                val token = parts.firstOrNull { it.contains("token=") }?.substringAfter("token=")?.trim()
                return validate(host, token)
            }

            return null
        }

        private fun validate(host: String?, token: String?): PairingProfile? {
            val cleanHost = host?.trim() ?: return null
            val cleanToken = token?.trim() ?: return null
            if (cleanHost.isBlank() || cleanToken.isBlank()) return null
            val isHostValid = runCatching { PcEndpoint.base(cleanHost) }.isSuccess
            if (!isHostValid) return null
            if (!PcEndpoint.validToken(cleanToken)) return null
            return PairingProfile(cleanHost, cleanToken)
        }
    }
}
