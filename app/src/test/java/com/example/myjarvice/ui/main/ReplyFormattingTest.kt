package com.example.myjarvice.ui.main

import org.junit.Assert.*
import org.junit.Test

class ReplyFormattingTest {
    @Test fun plainTextIsPreserved() { assertEquals("Hello\nworld", formatReplyText("Hello\nworld").text) }
    @Test fun emphasisAndCodeAreReadable() {
        val result = formatReplyText("Use **clear words** and `code`.")
        assertEquals("Use clear words and code.", result.text)
        assertTrue(result.spanStyles.isNotEmpty())
    }
    @Test fun headingsAndBulletsAreReadable() { assertEquals("Plan\n• First step", formatReplyText("## Plan\n- First step").text) }
    @Test fun fencedCodePreservesLiteralMarkup() {
        assertEquals("\n  **literal**\n", formatReplyText("```text\n  **literal**\n```").text)
    }
}
