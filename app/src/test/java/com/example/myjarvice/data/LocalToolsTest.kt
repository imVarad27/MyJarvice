package com.example.myjarvice.data

import org.junit.Assert.*
import org.junit.Test

class LocalToolsTest {
    @Test fun arithmeticUsesPrecedenceAndDecimalMath() {
        assertEquals("100", LocalCalculator.evaluate("(18 + 7) * 4"))
        assertEquals("0.3", LocalCalculator.evaluate("0.1 + 0.2"))
        assertEquals("-6", LocalCalculator.evaluate("2 * -(1+2)"))
        assertEquals("14", LocalCalculator.evaluate("2+3*4"))
    }
    @Test fun invalidArithmeticNeverExecutes() {
        listOf("1/0", "1+", "(2+3", "System.exit(0)", "1 2", "9".repeat(201)).forEach {
            assertTrue(it, runCatching { LocalCalculator.evaluate(it) }.isFailure)
        }
    }
    @Test fun retrievalRanksRelevantPassagesAndBoundsContext() {
        val entries = listOf(
            KnowledgeEntry("1", "Timetable", "The physics examination starts at 10 AM.", false),
            KnowledgeEntry("2", "Groceries", "Buy apples and carrots.", false)
        )
        val hits = LocalKnowledgeStore.rank("When is the physics examination?", entries)
        assertEquals(1, hits.size)
        assertTrue(hits.first().source.startsWith("Timetable"))
        assertTrue(LocalKnowledgeStore.rank("volcano", entries).isEmpty())
        val many = LocalKnowledgeStore.rank("physics", List(20) { entries[0] })
        assertEquals(3, many.size)
        assertTrue(many.all { it.text.length <= 700 })
    }
}
