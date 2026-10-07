package com.example.myjarvice.data

import okhttp3.HttpUrl
import kotlinx.serialization.json.*

/** Preserve secure schemes and never place a pairing token into a URL. */
internal object ActivityApiPolicy {
    fun endpoint(address: String): HttpUrl {
        return PcEndpoint.api(address, "/api/audit/recent").newBuilder().query("limit=50").build()
    }

    fun decode(text: String): List<ActionAuditEvent> {
        require(text.toByteArray(Charsets.UTF_8).size <= ActionTimeline.MAX_FILE_BYTES) { "PC activity response is too large." }
        val array = (Json.parseToJsonElement(text) as? JsonObject)?.get("events") as? JsonArray
            ?: error("The PC returned invalid activity data.")
        require(array.size <= 100) { "Too many PC activity records." }
        return array.map { element ->
            val item = element as? JsonObject ?: error("Invalid PC activity record.")
            fun string(key: String, limit: Int): String = (item[key] as? JsonPrimitive)?.takeIf { it.isString }?.content?.take(limit)
                ?: error("Invalid PC activity field.")
            val id = (item["id"] as? JsonPrimitive)?.takeIf { !it.isString }?.longOrNull ?: error("Invalid PC activity ID.")
            require(id > 0) { "Invalid PC activity ID." }
            ActionAuditEvent(id, string("created_at", 40), string("action_type", 120), string("outcome", 40), string("summary", 320), source = "pc")
        }
    }
}
