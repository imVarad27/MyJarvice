package com.example.myjarvice.data

import org.junit.Assert.*
import org.junit.Test

class PairingProfileTest {

    @Test
    fun `parse uri scheme successfully extracts host and token`() {
        val uri = "jarvis-pair://v1?host=192.168.1.25%3A8000&token=secret-token-xyz-123"
        val profile = PairingProfile.parse(uri)

        assertNotNull(profile)
        assertEquals("192.168.1.25:8000", profile!!.host)
        assertEquals("secret-token-xyz-123", profile.token)
    }

    @Test
    fun `parse json snippet extracts host and token`() {
        val json = """{"host": "10.0.0.4:8000", "token": "test-token-456"}"""
        val profile = PairingProfile.parse(json)

        assertNotNull(profile)
        assertEquals("10.0.0.4:8000", profile!!.host)
        assertEquals("test-token-456", profile.token)
    }

    @Test
    fun `parse env snippet extracts host and token`() {
        val env = """
            # Server config
            HOST=192.168.0.50:8000
            JARVICE_API_TOKEN=env-token-789
        """.trimIndent()
        val profile = PairingProfile.parse(env)

        assertNotNull(profile)
        assertEquals("192.168.0.50:8000", profile!!.host)
        assertEquals("env-token-789", profile.token)
    }

    @Test
    fun `parse semicolon format extracts host and token`() {
        val semi = "192.168.1.99:8000;token=semi-token-abc"
        val profile = PairingProfile.parse(semi)

        assertNotNull(profile)
        assertEquals("192.168.1.99:8000", profile!!.host)
        assertEquals("semi-token-abc", profile.token)
    }

    @Test
    fun `export and parse round trip`() {
        val host = "http://192.168.2.10:8000"
        val token = "round-trip-token-sample"
        val exported = PairingProfile.exportUri(host, token)

        val parsed = PairingProfile.parse(exported)
        assertNotNull(parsed)
        assertEquals(host, parsed!!.host)
        assertEquals(token, parsed.token)
    }

    @Test
    fun `rejects invalid or unsafe inputs`() {
        assertNull(PairingProfile.parse(""))
        assertNull(PairingProfile.parse("   "))
        assertNull(PairingProfile.parse("some random text with no format"))
        assertNull(PairingProfile.parse("jarvis-pair://v1?host=admin:pass@host:8000&token=abc")) // credentials rejected
        assertNull(PairingProfile.parse("""{"host": "invalid\\host", "token": "abc"}"""))
        assertNull(PairingProfile.parse("""{"host": "192.168.1.1:8000", "token": "token with spaces"}"""))
    }

    @Test
    fun `maskedToken hides sensitive secret`() {
        val short = PairingProfile("host", "12345")
        assertEquals("••••••••", short.maskedToken)

        val long = PairingProfile("host", "supersecrettoken99")
        assertEquals("supe••••en99", long.maskedToken)
        assertFalse(long.maskedToken.contains("secret"))
    }
}
