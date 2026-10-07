package com.example.myjarvice.data

import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.serialization.json.*
import okhttp3.*
import java.io.IOException
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLException
import kotlin.coroutines.resume

enum class PcCheck(val title: String, val advice: String) {
    INSTALLED("PC model is installed", "The host accepted your token. Chat connection and generation still need a real request."),
    MISSING_MODEL("Configured model is missing", "On the PC, check ollama list and JARVIS_MODEL in server/.env. Choose an installed model or download one yourself."),
    RUNTIME_OFFLINE("Ollama isn't reachable", "Start Ollama on the PC, then run this check again. Jarvis's web server and the model runtime are separate."),
    RUNTIME_INVALID("Unexpected Ollama response", "Check the Ollama installation and server runtime address. No model was started by this check."),
    HOST_CONFIG("Host model setup needs attention", "Check JARVIS_MODEL and the Ollama address on your PC."),
    UNREACHABLE("Can't reach the PC", "Keep the PC awake and start the Jarvis server. Check the address, port and firewall; guest Wi-Fi/VPNs can block local traffic. USB localhost requires adb reverse tcp:8000 tcp:8000 on the PC."),
    TOKEN_REJECTED("Pairing token rejected", "Copy the exact JARVICE_API_TOKEN from your PC's server/.env. Do not share it in chat or screenshots."),
    TOKEN_REQUIRED("Enter your pairing token", "Use your PC's JARVICE_API_TOKEN. Jarvis does not substitute a default token."),
    OLD_SERVER("Health check isn't available", "Update and restart the PC server. Older servers may still support chat without this endpoint."),
    INVALID_ADDRESS("Check the PC address", "Use host:port or an HTTP/HTTPS/WS/WSS address. Do not include credentials, query parameters or fragments."),
    TLS_ERROR("Secure connection failed", "Check the PC's HTTPS certificate and hostname. Jarvis will not bypass certificate checks or downgrade to HTTP."),
    REDIRECT("Server redirect was blocked", "Enter the final trusted PC address directly. Your token was not forwarded to the redirect destination."),
    INVALID_RESPONSE("This isn't a supported health response", "Check that this address points to the updated Jarvis server, not another service."),
    HOST_UNAVAILABLE("PC service is unavailable", "The server returned an error. Check its logs and pairing configuration on the PC.")
}

data class PcDiagnosticReport(val check: PcCheck, val model: String? = null, val loaded: Boolean? = null,
                              val elapsedMs: Long? = null)

object PcHealthProtocol {
    const val MAX_BYTES = 16 * 1024
    fun decode(text: String): PcDiagnosticReport {
        require(text.toByteArray(Charsets.UTF_8).size <= MAX_BYTES)
        val root = Json.parseToJsonElement(text) as? JsonObject ?: error("Invalid health response")
        fun string(key: String) = (root[key] as? JsonPrimitive)?.takeIf { it.isString }?.content
        require(string("service") == "jarvis")
        val version = root["health_version"] as? JsonPrimitive
        require(version != null && !version.isString && version.intOrNull == 1)
        val model = string("model").orEmpty()
        require(model.isEmpty() || Regex("[A-Za-z0-9._:/-]{1,120}").matches(model))
        val loadedValue = root["loaded"]
        require(loadedValue == JsonNull || (loadedValue is JsonPrimitive && !loadedValue.isString && loadedValue.booleanOrNull != null))
        val loaded = (loadedValue as? JsonPrimitive)?.booleanOrNull
        val check = when (string("runtime")) {
            "unreachable" -> PcCheck.RUNTIME_OFFLINE
            "invalid_response" -> PcCheck.RUNTIME_INVALID
            "unknown" -> PcCheck.HOST_CONFIG
            "reachable" -> when (string("model_status")) {
                "installed" -> { require(model.isNotEmpty()); PcCheck.INSTALLED }
                "missing" -> { require(model.isNotEmpty()); PcCheck.MISSING_MODEL }
                else -> error("Invalid model status")
            }
            else -> error("Invalid runtime status")
        }
        return PcDiagnosticReport(check, model.takeIf { it.isNotEmpty() }, loaded)
    }
}

/** One explicit authenticated GET, bounded time/body, no redirects, no chat/user content. */
class ConnectionDiagnostics(client: OkHttpClient = OkHttpClient()) {
    private val client = client.newBuilder()
        .connectTimeout(3, TimeUnit.SECONDS).readTimeout(6, TimeUnit.SECONDS).callTimeout(9, TimeUnit.SECONDS)
        .followRedirects(false).followSslRedirects(false).retryOnConnectionFailure(false).build()
    suspend fun check(address: String, token: String): PcDiagnosticReport {
        val url = runCatching { PcEndpoint.api(address, "/api/health") }.getOrNull()
            ?: return PcDiagnosticReport(PcCheck.INVALID_ADDRESS)
        if (!PcEndpoint.validToken(token)) return PcDiagnosticReport(PcCheck.TOKEN_REQUIRED)
        val started = System.nanoTime()
        val call = client.newCall(Request.Builder().url(url).header("Authorization", "Bearer ${token.trim()}").build())
        return suspendCancellableCoroutine { continuation ->
            continuation.invokeOnCancellation { call.cancel() }
            fun finish(report: PcDiagnosticReport) {
                if (continuation.isActive) continuation.resume(report.copy(elapsedMs = (System.nanoTime() - started) / 1_000_000))
            }
            call.enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    finish(PcDiagnosticReport(if (e is SSLException) PcCheck.TLS_ERROR else PcCheck.UNREACHABLE))
                }
                override fun onResponse(call: Call, response: Response) {
                    val report = response.use {
                        when (it.code) {
                            401, 403 -> PcDiagnosticReport(PcCheck.TOKEN_REJECTED)
                            404 -> PcDiagnosticReport(PcCheck.OLD_SERVER)
                            in 300..399 -> PcDiagnosticReport(PcCheck.REDIRECT)
                            200 -> runCatching {
                                val body = it.body ?: error("No health body")
                                require(body.contentLength() <= PcHealthProtocol.MAX_BYTES)
                                body.byteStream().use { input ->
                                    val bytes = java.io.ByteArrayOutputStream()
                                    val buffer = ByteArray(1024)
                                    while (true) {
                                        val count = input.read(buffer)
                                        if (count < 0) break
                                        require(bytes.size() + count <= PcHealthProtocol.MAX_BYTES)
                                        bytes.write(buffer, 0, count)
                                    }
                                    PcHealthProtocol.decode(bytes.toByteArray().toString(Charsets.UTF_8))
                                }
                            }.getOrElse { PcDiagnosticReport(PcCheck.INVALID_RESPONSE) }
                            else -> PcDiagnosticReport(PcCheck.HOST_UNAVAILABLE)
                        }
                    }
                    finish(report)
                }
            })
        }
    }
}
