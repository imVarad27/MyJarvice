package com.example.myjarvice.data

import android.content.Context
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

data class ChatSession(
    val id: String,
    val title: String,
    val createdAt: Long,
    val updatedAt: Long,
    val messages: List<JarvisMessage>
)

class ChatHistoryStore(context: Context) {

    private val file = File(context.filesDir, "jarvis_chat_sessions.json")

    @Synchronized
    fun loadAllSessions(): List<ChatSession> {
        if (!file.exists()) return emptyList()
        return try {
            val text = file.readText()
            if (text.isBlank()) return emptyList()

            val jsonArray = JSONArray(text)
            val sessions = mutableListOf<ChatSession>()

            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                val id = obj.getString("id")
                val title = obj.optString("title", "Conversation")
                val createdAt = obj.optLong("createdAt", System.currentTimeMillis())
                val updatedAt = obj.optLong("updatedAt", createdAt)

                val msgArray = obj.optJSONArray("messages") ?: JSONArray()
                val messages = mutableListOf<JarvisMessage>()

                for (j in 0 until msgArray.length()) {
                    val msgObj = msgArray.getJSONObject(j)
                    val img = if (msgObj.has("image") && !msgObj.isNull("image")) msgObj.getString("image") else null
                    val sourcesList = mutableListOf<WebSource>()
                    if (msgObj.has("sources") && !msgObj.isNull("sources")) {
                        val srcArray = msgObj.getJSONArray("sources")
                        for (k in 0 until srcArray.length()) {
                            val sObj = srcArray.getJSONObject(k)
                            sourcesList.add(
                                WebSource(
                                    title = sObj.optString("title", ""),
                                    url = sObj.optString("url", ""),
                                    domain = sObj.optString("domain", "web")
                                )
                            )
                        }
                    }

                    messages.add(
                        JarvisMessage(
                            sender = msgObj.optString("sender", "USER"),
                            text = msgObj.optString("text", ""),
                            type = msgObj.optString("type", "RESPONSE"),
                            timestamp = msgObj.optString("timestamp", ""),
                            image = img,
                            sources = sourcesList
                        )
                    )
                }




                sessions.add(
                    ChatSession(
                        id = id,
                        title = title,
                        createdAt = createdAt,
                        updatedAt = updatedAt,
                        messages = messages
                    )
                )
            }
            sessions.sortedByDescending { it.updatedAt }
        } catch (e: Exception) {
            Log.e("ChatHistoryStore", "Error loading chat sessions: ${e.message}", e)
            emptyList()
        }
    }

    @Synchronized
    fun saveSession(session: ChatSession) {
        if (session.messages.isEmpty()) return
        try {
            val sessions = loadAllSessions().toMutableList()
            val existingIndex = sessions.indexOfFirst { it.id == session.id }

            if (existingIndex >= 0) {
                sessions[existingIndex] = session.copy(updatedAt = System.currentTimeMillis())
            } else {
                sessions.add(0, session.copy(updatedAt = System.currentTimeMillis()))
            }

            persistSessions(sessions)
        } catch (e: Exception) {
            Log.e("ChatHistoryStore", "Error saving session: ${e.message}", e)
        }
    }

    @Synchronized
    fun deleteSession(sessionId: String) {
        try {
            val sessions = loadAllSessions().filterNot { it.id == sessionId }
            persistSessions(sessions)
        } catch (e: Exception) {
            Log.e("ChatHistoryStore", "Error deleting session: ${e.message}", e)
        }
    }

    @Synchronized
    fun clearAll() {
        try {
            if (file.exists()) file.delete()
        } catch (e: Exception) {
            Log.e("ChatHistoryStore", "Error clearing sessions: ${e.message}", e)
        }
    }

    private fun persistSessions(sessions: List<ChatSession>) {
        val jsonArray = JSONArray()
        for (session in sessions) {
            val obj = JSONObject().apply {
                put("id", session.id)
                put("title", session.title)
                put("createdAt", session.createdAt)
                put("updatedAt", session.updatedAt)

                val msgArray = JSONArray()
                for (msg in session.messages) {
                    val msgObj = JSONObject().apply {
                        put("sender", msg.sender)
                        put("text", msg.text)
                        put("type", msg.type)
                        put("timestamp", msg.timestamp)
                        if (msg.image != null) {
                            put("image", msg.image)
                        }
                        if (msg.sources.isNotEmpty()) {
                            val srcArray = JSONArray()
                            for (s in msg.sources) {
                                srcArray.put(
                                    JSONObject().apply {
                                        put("title", s.title)
                                        put("url", s.url)
                                        put("domain", s.domain)
                                    }
                                )
                            }
                            put("sources", srcArray)
                        }
                    }
                    msgArray.put(msgObj)


                }
                put("messages", msgArray)
            }
            jsonArray.put(obj)
        }
        file.writeText(jsonArray.toString(2))
    }
}
