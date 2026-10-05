package com.example.myjarvice.data

import org.junit.Assert.*
import org.junit.Test

class ActivityApiPolicyTest {
    @Test fun secureAddressesAreNeverDowngraded() {
        assertEquals("https://example.com:8443/api/audit/recent?limit=50", ActivityApiPolicy.endpoint("wss://example.com:8443/ws/jarvis").toString())
        assertEquals("https://example.com/api/audit/recent?limit=50", ActivityApiPolicy.endpoint("https://example.com/anything").toString())
        assertEquals("http://127.0.0.1:8000/api/audit/recent?limit=50", ActivityApiPolicy.endpoint("127.0.0.1:8000").toString())
        assertEquals("http://localhost:8000/api/audit/recent?limit=50", ActivityApiPolicy.endpoint("ws://localhost:8000/ws/jarvis").toString())
    }

    @Test fun credentialAndQueryBearingAddressesAreRejected() {
        listOf("", "https://user:pass@example.com", "https://example.com?token=secret", "https://example.com#fragment", "file:///tmp/data").forEach {
            assertTrue(it, runCatching { ActivityApiPolicy.endpoint(it) }.isFailure)
        }
    }

    @Test fun validRemoteRecordsAreBoundedAndAlwaysLabeledPc() {
        val row = """{"id":1,"created_at":"2026-10-05T04:30:00+00:00","action_type":"pc_status","outcome":"completed","summary":"Read PC status","source":"phone"}"""
        val events = ActivityApiPolicy.decode("""{"events":[$row]}""")
        assertEquals("pc", events.single().source)
        assertEquals("PC status", ActionTimeline.label(events.single().actionType))
        assertTrue(runCatching { ActivityApiPolicy.decode("""{"events":[${List(101) { row }.joinToString()}]}""") }.isFailure)
        assertTrue(runCatching { ActivityApiPolicy.decode("{}") }.isFailure)
        assertTrue(runCatching { ActivityApiPolicy.decode("""{"events":[{"id":-1}]}""") }.isFailure)
        assertTrue(runCatching { ActivityApiPolicy.decode("""{"events":[{"id":"1"}]}""") }.isFailure)
    }
}
