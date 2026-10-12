package com.example.myjarvice.data

import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.Executors
import java.util.concurrent.Callable

class ReplyShareApprovalTest {
    @Test fun `only bounded completed assistant text is eligible`() {
        assertTrue(ReplyShareApproval.canReview("Jarvis (On-device)", "RESPONSE", "Hello"))
        assertTrue(ReplyShareApproval.canReview("Jarvis", "RESPONSE", "x".repeat(20_000)))
        assertFalse(ReplyShareApproval.canReview("USER", "RESPONSE", "Private user text"))
        for (type in listOf("PARTIAL", "ERROR", "ACTION", "")) {
            assertFalse(ReplyShareApproval.canReview("Jarvis", type, "Some text"))
        }
        for (text in listOf("", " \n\t", "x".repeat(20_001))) {
            assertFalse(ReplyShareApproval.canReview("Jarvis", "RESPONSE", text))
        }
    }

    @Test fun `approval preserves exact text including spacing unicode and source text`() {
        val body = "  नमस्ते 🦉\n\nRetrieved sources:\nhttps://example.test/?a=1  "
        val approval = ReplyShareApproval.prepare(body)
        assertEquals(body, approval.text)
        assertEquals(body, approval.consume(body))
        assertNull(approval.consume(body))
    }

    @Test fun `changed text invalidates rather than reusing old approval`() {
        val approval = ReplyShareApproval.prepare("Previewed text")
        assertNull(approval.consume("Edited text"))
        assertNull(approval.consume("Previewed text"))
    }

    @Test fun `cancel cannot later authorize sharing`() {
        val approval = ReplyShareApproval.prepare("Hello")
        approval.cancel(); approval.cancel()
        assertNull(approval.consume("Hello"))
        assertEquals("Hello", ReplyShareApproval.prepare("Hello").consume("Hello"))
    }

    @Test fun `racing approval clicks produce one handoff only`() {
        val approval = ReplyShareApproval.prepare("Hello")
        val pool = Executors.newFixedThreadPool(4)
        try {
            val results = pool.invokeAll(List(20) { Callable { approval.consume("Hello") } }).map { it.get() }
            assertEquals(1, results.count { it != null })
        } finally { pool.shutdownNow() }
    }

    @Test(expected = IllegalArgumentException::class) fun `blank snapshot is rejected`() {
        ReplyShareApproval.prepare(" \n ")
    }

    @Test(expected = IllegalArgumentException::class) fun `oversized snapshot is rejected without truncation`() {
        ReplyShareApproval.prepare("x".repeat(20_001))
    }
}
