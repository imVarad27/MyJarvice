package com.example.myjarvice.data

import org.junit.Assert.*
import org.junit.Test

class PcEndpointTest {
    @Test fun bareHostsDefaultToJarvisPortAndExplicitUrisKeepTheirPort() {
        assertEquals("ws://pc.local:8000/ws/jarvis", PcEndpoint.websocket("pc.local"))
        assertEquals("ws://192.168.1.4:8080/ws/jarvis", PcEndpoint.websocket("192.168.1.4:8080"))
        assertEquals("ws://[::1]:8000/ws/jarvis", PcEndpoint.websocket("[::1]"))
        assertEquals("ws://pc.local/ws/jarvis", PcEndpoint.websocket("http://pc.local"))
        assertEquals("wss://example.com:8443/ws/jarvis", PcEndpoint.websocket("https://example.com:8443"))
        assertEquals("wss://example.com/ws/jarvice", PcEndpoint.websocket("WSS://example.com/ws/jarvice"))
    }
    @Test fun apiAndSocketUseTheSameSecureOrigin() {
        assertEquals("https://example.com:8443/api/health", PcEndpoint.api("wss://example.com:8443/ws/jarvis", "/api/health").toString())
        assertEquals("http://pc.local:8000/api/health", PcEndpoint.api("pc.local", "/api/health").toString())
        assertTrue(runCatching { PcEndpoint.api("pc.local", "/api/delete") }.isFailure)
        assertEquals("https://example.com:8443", FileTransferManager.getBaseUrl("wss://example.com:8443/ws/jarvis"))
        assertEquals("http://pc.local:8000", FileTransferManager.getBaseUrl("pc.local"))
    }
    @Test fun ambiguousOrCredentialBearingAddressesAreRejected() {
        listOf("", "file:///private", "http://user:password@pc.local", "https://pc.local?token=secret", "https://pc.local#fragment",
            "http://pc.local:0", "pc.local:99999", "pc local", "http://pc.local\\path", "pc.local\nprivate").forEach {
            assertTrue(it, runCatching { PcEndpoint.base(it) }.isFailure)
        }
    }
    @Test fun blankUnsafeAndExcessiveTokensAreRejectedWithoutDefaultFallback() {
        assertFalse(PcEndpoint.validToken(""))
        assertFalse(PcEndpoint.validToken("Bearer other"))
        assertFalse(PcEndpoint.validToken("secret\nheader"))
        assertFalse(PcEndpoint.validToken("x".repeat(513)))
        assertTrue(PcEndpoint.validToken(" synthetic-token "))
        assertEquals("", SettingsStore.DEFAULT_SERVER_TOKEN)
    }
}
