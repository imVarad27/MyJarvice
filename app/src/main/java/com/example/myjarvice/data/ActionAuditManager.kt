package com.example.myjarvice.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/** A privacy-minimised record returned by the paired Jarvis PC. */
data class ActionAuditEvent(
    val id: Long,
    val createdAt: String,
    val actionType: String,
    val outcome: String,
    val summary: String
)

object ActionAuditManager {
    private fun baseUrl(serverIp: String): String {
        val clean = serverIp.trim()
            .removePrefix("http://").removePrefix("https://")
            .removePrefix("ws://").removePrefix("wss://")
        return "http://${clean.substringBefore('/')}"
    }

    suspend fun recent(serverIp: String, token: String): Result<List<ActionAuditEvent>> = withContext(Dispatchers.IO) {
        try {
            val connection = (URL("${baseUrl(serverIp)}/api/audit/recent?limit=50").openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 6_000
                readTimeout = 10_000
                setRequestProperty("Authorization", "Bearer $token")
            }
            val responseCode = connection.responseCode
            if (responseCode !in 200..299) {
                val detail = connection.errorStream?.bufferedReader()?.readText() ?: "HTTP $responseCode"
                return@withContext Result.failure(IllegalStateException(detail))
            }
            val array = JSONObject(connection.inputStream.bufferedReader().readText()).optJSONArray("events")
            val events = buildList {
                if (array != null) for (index in 0 until array.length()) {
                    val item = array.getJSONObject(index)
                    add(ActionAuditEvent(
                        id = item.optLong("id"),
                        createdAt = item.optString("created_at"),
                        actionType = item.optString("action_type"),
                        outcome = item.optString("outcome"),
                        summary = item.optString("summary")
                    ))
                }
            }
            Result.success(events)
        } catch (error: Exception) {
            Result.failure(error)
        }
    }
}
