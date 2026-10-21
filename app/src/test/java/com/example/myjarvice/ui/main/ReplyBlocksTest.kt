package com.example.myjarvice.ui.main

import org.junit.Assert.*
import org.junit.Test

class ReplyBlocksTest {
    @Test fun plainTextAndParagraphBreaksRemainIntact() {
        assertEquals(listOf(ReplyBlock.Prose("Hello\n\nworld")), replyBlocks("Hello\n\nworld"))
    }
    @Test fun fencesSeparateLanguageAndPreserveLiteralCode() {
        assertEquals(listOf(ReplyBlock.Prose("Example"), ReplyBlock.Code("kotlin", "  **literal**\nprintln(1)"), ReplyBlock.Prose("Done")),
            replyBlocks("Example\n```kotlin\n  **literal**\nprintln(1)\n```\nDone"))
    }
    @Test fun unfinishedStreamingFenceDoesNotLoseCode() {
        assertEquals(listOf(ReplyBlock.Code("python", "print('你好')")), replyBlocks("```python\nprint('你好')"))
    }
    @Test fun longerOuterFenceAllowsInnerBackticks() {
        assertEquals(listOf(ReplyBlock.Code("markdown", "```\ninside\n```")), replyBlocks("````markdown\n```\ninside\n```\n````"))
    }
    @Test fun mismatchedFenceCannotCloseCode() {
        assertEquals(listOf(ReplyBlock.Code("text", "keep\n~~~\nend")), replyBlocks("```text\nkeep\n~~~\nend\n```"))
    }
    @Test fun tildeFenceAndEmptyCodeAreHandled() {
        assertEquals(listOf(ReplyBlock.Code("", "")), replyBlocks("~~~\n~~~"))
    }
    @Test fun fenceLookingProseIsNotDropped() {
        val text = "Before ```inline``` after\n```not a language\nDone"
        assertEquals(listOf(ReplyBlock.Prose(text)), replyBlocks(text))
        assertTrue(formatReplyText("```not a language", interpretFences = false).text.contains("not a language"))
    }
    @Test fun windowsLineEndingsHaveTheSameVisibleCode() {
        assertEquals(replyBlocks("```cs\nvar x = 1;\n```"), replyBlocks("```cs\r\nvar x = 1;\r\n```"))
    }
    @Test fun htmlAndCommandsRemainLiteralData() {
        val text = "<script>send()</script>\nrm -rf /"
        assertEquals(listOf(ReplyBlock.Code("html", text)), replyBlocks("```html\n$text\n```"))
    }
}
