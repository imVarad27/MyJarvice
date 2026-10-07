package com.example.myjarvice.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.serialization.Serializable
import kotlinx.coroutines.CancellationException

/** Metadata from this phone or the paired Jarvis PC; source keeps identities separate. */
@Serializable
data class ActionAuditEvent(
    val id: Long,
    val createdAt: String,
    val actionType: String,
    val outcome: String,
    val summary: String,
    val source: String = "pc"
)

object ActionAuditManager {
    suspend fun recent(serverIp: String, token: String): Result<List<ActionAuditEvent>> = withContext(Dispatchers.IO) {
        var connection: HttpURLConnection? = null
        try {
            require(PcEndpoint.validToken(token)) { "Pair your PC before loading its activity." }
            connection = (URL(ActivityApiPolicy.endpoint(serverIp).toString()).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 6_000
                readTimeout = 10_000
                setRequestProperty("Authorization", "Bearer $token")
                instanceFollowRedirects = false
            }
            val responseCode = connection.responseCode
            if (responseCode !in 200..299) {
                return@withContext Result.failure(IllegalStateException("PC activity unavailable (HTTP $responseCode)."))
            }
            val text = connection.inputStream.use { PhoneActivityJournal.readBounded(it).toString(Charsets.UTF_8) }
            Result.success(ActivityApiPolicy.decode(text))
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            Result.failure(error)
        } finally { connection?.disconnect() }
    }
}
