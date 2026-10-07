package com.example.myjarvice.data

import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLHandshakeException

class ConnectionDiagnosticsTest {
    private val healthy = """{"service":"jarvis","health_version":1,"runtime":"reachable","model":"synthetic:latest","model_status":"installed","loaded":false}"""

    private fun response(request: Request, code: Int = 200, body: String = healthy, location: String? = null): Response =
        Response.Builder().request(request).protocol(Protocol.HTTP_1_1).code(code).message("Synthetic")
            .body(body.toResponseBody("application/json".toMediaType())).apply { location?.let { header("Location", it) } }.build()

    private fun withClient(interceptor: Interceptor, test: (ConnectionDiagnostics) -> Unit) {
        val client = OkHttpClient.Builder().addInterceptor(interceptor).build()
        try { test(ConnectionDiagnostics(client)) }
        finally { client.connectionPool.evictAll(); client.dispatcher.executorService.shutdownNow() }
    }

    @Test fun checksOnlyTheAuthenticatedHealthEndpointWithoutUserPayload() {
        withClient(Interceptor { chain ->
            assertEquals("GET", chain.request().method)
            assertEquals("https://example.com:8443/api/health", chain.request().url.toString())
            assertEquals("Bearer synthetic-token", chain.request().header("Authorization"))
            assertNull(chain.request().body)
            response(chain.request())
        }) { diagnostics ->
            val result = runBlocking { diagnostics.check("wss://example.com:8443/ws/jarvis", "synthetic-token") }
            assertEquals(PcCheck.INSTALLED, result.check)
            assertEquals(false, result.loaded)
            assertTrue(result.elapsedMs!! >= 0)
        }
    }

    @Test fun invalidAddressOrTokenNeverReachesNetwork() {
        withClient(Interceptor { error("No network request allowed") }) { diagnostics ->
            assertEquals(PcCheck.INVALID_ADDRESS, runBlocking { diagnostics.check("http://user:secret@example.com", "token") }.check)
            assertEquals(PcCheck.TOKEN_REQUIRED, runBlocking { diagnostics.check("pc.local", "") }.check)
            assertEquals(PcCheck.TOKEN_REQUIRED, runBlocking { diagnostics.check("pc.local", "bad\nheader") }.check)
        }
    }

    @Test fun redirectsAreNotFollowedAndRawErrorsAreNotShown() {
        val calls = AtomicInteger()
        withClient(Interceptor { chain -> calls.incrementAndGet(); response(chain.request(), 302, "private raw body", "https://other.example/private") }) { diagnostics ->
            val report = runBlocking { diagnostics.check("pc.local", "synthetic-token") }
            assertEquals(PcCheck.REDIRECT, report.check)
            assertEquals(1, calls.get())
            assertFalse(report.toString().contains("private raw"))
            assertFalse(report.toString().contains("synthetic-token"))
        }
    }

    @Test fun rejectionOldServerAndHttpErrorsStayDistinct() {
        mapOf(401 to PcCheck.TOKEN_REJECTED, 403 to PcCheck.TOKEN_REJECTED, 404 to PcCheck.OLD_SERVER, 503 to PcCheck.HOST_UNAVAILABLE).forEach { (code, expected) ->
            withClient(Interceptor { response(it.request(), code, "private server text") }) { diagnostics ->
                assertEquals(expected, runBlocking { diagnostics.check("pc.local", "synthetic") }.check)
            }
        }
    }

    @Test fun oversizedBodiesAndWrongServicesAreNotReportedAsHealthy() {
        listOf("x".repeat(PcHealthProtocol.MAX_BYTES + 1), healthy.replace("jarvis", "other")).forEach { body ->
            withClient(Interceptor { response(it.request(), body = body) }) { diagnostics ->
                assertEquals(PcCheck.INVALID_RESPONSE, runBlocking { diagnostics.check("pc.local", "synthetic") }.check)
            }
        }
    }

    @Test fun protocolReportsColdMissingAndStoppedRuntimeHonestly() {
        assertEquals(false, PcHealthProtocol.decode(healthy).loaded)
        assertEquals(PcCheck.MISSING_MODEL, PcHealthProtocol.decode(healthy.replace("installed", "missing")).check)
        assertEquals(PcCheck.RUNTIME_OFFLINE, PcHealthProtocol.decode(healthy.replace("reachable", "unreachable")).check)
        assertEquals(PcCheck.RUNTIME_INVALID, PcHealthProtocol.decode(healthy.replace("reachable", "invalid_response")).check)
        assertNull(PcHealthProtocol.decode(healthy.replace("\"loaded\":false", "\"loaded\":null")).loaded)
    }

    @Test fun malformedProtocolAndQuotedConsentFlagsAreRejected() {
        listOf("{}", healthy.replace("\"loaded\":false", "\"loaded\":\"false\""),
            healthy.replace("\"health_version\":1", "\"health_version\":\"1\""),
            healthy.replace("synthetic:latest", "private\\ntext"), healthy.replace("\"runtime\":\"reachable\"", "\"runtime\":\"invented\"")).forEach {
            assertTrue(runCatching { PcHealthProtocol.decode(it) }.isFailure)
        }
    }

    @Test fun cancellationAbortsTheCallerWithoutReturningAnOutdatedReport() {
        val started = CountDownLatch(1)
        val release = CountDownLatch(1)
        withClient(Interceptor { chain ->
            started.countDown()
            release.await(3, TimeUnit.SECONDS)
            response(chain.request())
        }) { diagnostics ->
            runBlocking {
                val pending = async { diagnostics.check("pc.local", "synthetic") }
                try {
                    assertTrue(withContext(Dispatchers.IO) { started.await(2, TimeUnit.SECONDS) })
                    pending.cancelAndJoin()
                    assertTrue(pending.isCancelled)
                } finally { release.countDown() }
            }
        }
    }

    @Test fun certificateErrorsAreNotDowngradedOrExposed() {
        withClient(Interceptor { throw SSLHandshakeException("private certificate details") }) { diagnostics ->
            val report = runBlocking { diagnostics.check("https://example.com", "synthetic") }
            assertEquals(PcCheck.TLS_ERROR, report.check)
            assertFalse(report.toString().contains("private certificate"))
        }
    }
}
