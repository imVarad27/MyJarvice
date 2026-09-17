package com.example.myjarvice.data

import org.junit.Assert.*
import org.junit.Test

class LocalConversationContextTest {
    @Test fun followUpRetrievesThePreviousUserTopicNotAssistantClaims() {
        val messages = listOf(JarvisMessage("USER", "physics exam timetable"), JarvisMessage("JARVIS", "unrelated hallucination"), JarvisMessage("USER", "Explain that"))
        assertEquals("physics exam timetable Explain that", LocalConversationContext.retrievalQuery("Explain that", messages))
        assertEquals("new topic", LocalConversationContext.retrievalQuery("new topic", messages))
    }
    @Test fun contextIsBoundedRecentAndSkipsErrors() {
        val messages = (1..20).map { JarvisMessage("USER", "$it " + "x".repeat(1000)) } + JarvisMessage("JARVIS", "private error", "ERROR")
        val context = LocalConversationContext.history(messages)
        assertTrue(context.length <= 1800)
        assertTrue(context.contains("20 "))
        assertFalse(context.contains("private error"))
        assertFalse(context.contains("User: 1 "))
    }
    @Test fun savedSearchNoiseDoesNotMatchAllMemories() {
        val entries = listOf(KnowledgeEntry("a", "Saved memory", "I enjoy tea", true))
        assertTrue(LocalKnowledgeStore.rank("find my saved memories", entries).isEmpty())
    }
    @Test fun examAliasAndSpecificBodyMatchBeatGenericTitle() {
        val entries = listOf(KnowledgeEntry("a", "Physics notes", "A very long generic discussion " + "word ".repeat(100), false),
            KnowledgeEntry("b", "Timetable", "Physics examination starts at 10 AM", false))
        val hits = LocalKnowledgeStore.rank("physics exam", entries)
        assertTrue(hits.first().source.startsWith("Timetable"))
    }
}
