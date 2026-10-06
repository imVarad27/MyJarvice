package com.example.myjarvice.data

import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream

class KnowledgeLibraryTest {
    private val fact = KnowledgeEntry("m", "Saved memory", "I prefer short answers.", true)
    private val document = KnowledgeEntry("d", "Physics notes", "The exam is on Friday.", false)

    @Test fun oldLibraryAndVersionOneBackupEntriesRemainReadable() {
        val legacy = """[{"id":"m","name":"Saved memory","text":"I prefer short answers.","memory":true}]"""
        assertEquals(fact, KnowledgeLibrary.decode(legacy).single())
        assertTrue(KnowledgeLibrary.decode(legacy).single().enabled)
    }

    @Test fun permissionsAndUnicodeSurvivePersistenceAndBackupCodec() {
        val rows = listOf(fact.copy(enabled = false, text = "I enjoy हिंदी and 日本語."), document)
        val encoded = KnowledgeLibrary.encode(rows)
        assertEquals(rows, KnowledgeLibrary.decode(encoded))
        assertTrue(encoded.contains("\"enabled\":false"))
    }

    @Test fun editPreservesIdentityExclusionAndUnrelatedDocuments() {
        val original = fact.copy(enabled = false)
        val edited = KnowledgeLibrary.editMemory(listOf(original, document), fact.id, "  Use bullet points.  ", fact.text)
        assertEquals(original.copy(text = "Use bullet points."), edited[0])
        assertEquals(document, edited[1])
    }

    @Test fun staleEditsMissingEntriesAndDocumentEditsAreRejected() {
        assertTrue(runCatching { KnowledgeLibrary.editMemory(listOf(fact), "missing", "New fact", fact.text) }.isFailure)
        assertTrue(runCatching { KnowledgeLibrary.editMemory(listOf(fact), fact.id, "New fact", "old value") }.isFailure)
        assertTrue(runCatching { KnowledgeLibrary.editMemory(listOf(document), document.id, "New fact", document.text) }.isFailure)
    }

    @Test fun invalidOrDuplicateMemoryEditsCannotChangeTheOriginal() {
        val rows = listOf(fact, fact.copy(id = "other", text = "Another fact"))
        listOf("", " ", "x".repeat(301), "text\u0000", "Another fact").forEach { text ->
            assertTrue(runCatching { KnowledgeLibrary.editMemory(rows, fact.id, text, fact.text) }.isFailure)
        }
        assertEquals("I prefer short answers.", rows[0].text)
    }

    @Test fun toggleKeepsContentAndRequiresAnExistingItem() {
        val disabled = KnowledgeLibrary.setEnabled(listOf(fact, document), fact.id, false)
        assertEquals(fact.copy(enabled = false), disabled[0])
        assertEquals(document, disabled[1])
        assertEquals(listOf(fact, document), KnowledgeLibrary.setEnabled(disabled, fact.id, true))
        assertTrue(runCatching { KnowledgeLibrary.setEnabled(disabled, "missing", true) }.isFailure)
    }

    @Test fun excludedContentNeverEntersRankedLibraryResults() {
        val rows = listOf(document.copy(enabled = false), fact)
        assertTrue(LocalKnowledgeStore.rank("physics exam Friday", rows).isEmpty())
        assertTrue(LocalKnowledgeStore.rank("short answers", rows).single().text.contains("short"))
        assertEquals(1, LocalKnowledgeStore.rank("physics exam", listOf(document)).size)
    }

    @Test fun searchAndFiltersStillAllowInspectionOfExcludedEntries() {
        val rows = listOf(fact, document.copy(enabled = false))
        assertEquals(rows[1], KnowledgeLibrary.visible(rows, "FRIDAY", KnowledgeFilter.ALL).single())
        assertEquals(listOf(fact), KnowledgeLibrary.visible(rows, "", KnowledgeFilter.MEMORIES))
        assertEquals(listOf(rows[1]), KnowledgeLibrary.visible(rows, "", KnowledgeFilter.EXCLUDED))
        assertTrue(KnowledgeLibrary.visible(rows, "answers", KnowledgeFilter.DOCUMENTS).isEmpty())
    }

    @Test fun deletionChecksReviewedSnapshotAndDoesNotTouchOtherEntries() {
        assertEquals(listOf(document), KnowledgeLibrary.deleteReviewed(listOf(fact, document), fact))
        assertTrue(runCatching { KnowledgeLibrary.deleteReviewed(listOf(fact.copy(text = "Changed")), fact) }.isFailure)
        assertTrue(runCatching { KnowledgeLibrary.deleteReviewed(listOf(fact.copy(enabled = false)), fact) }.isFailure)
        assertTrue(runCatching { KnowledgeLibrary.deleteReviewed(emptyList(), fact) }.isFailure)
    }

    @Test fun invalidPermissionFlagsDuplicatesAndMalformedStorageAreRejected() {
        val encoded = KnowledgeLibrary.encode(listOf(fact))
        assertTrue(runCatching { KnowledgeLibrary.decode(encoded.replace("\"enabled\":true", "\"enabled\":\"true\"")) }.isFailure)
        assertTrue(runCatching { KnowledgeLibrary.decode(encoded.replace("\"enabled\":true", "\"enabled\":null")) }.isFailure)
        assertTrue(runCatching { KnowledgeLibrary.decode("not json") }.isFailure)
        assertTrue(runCatching { KnowledgeLibrary.encode(listOf(fact, fact)) }.isFailure)
        assertTrue(runCatching { KnowledgeLibrary.encode(List(51) { fact.copy(id = "$it") }) }.isFailure)
        assertTrue(runCatching { KnowledgeLibrary.encode(List(21) { document.copy(id = "$it") }) }.isFailure)
        assertTrue(runCatching { KnowledgeLibrary.encode(listOf(document.copy(text = "x".repeat(100_001)))) }.isFailure)
    }

    @Test fun oversizedStorageIsRejectedBeforeParsing() {
        assertTrue(runCatching { KnowledgeLibrary.readBounded(ByteArrayInputStream(ByteArray(KnowledgeLibrary.MAX_BYTES + 1))) }.isFailure)
        assertEquals("[]", KnowledgeLibrary.readBounded(ByteArrayInputStream("[]".toByteArray())))
    }

    @Test fun malformedBooleanAndProviderErrorsCannotBecomeConsentOrUiContent() {
        val encoded = KnowledgeLibrary.encode(listOf(fact))
        assertTrue(runCatching { KnowledgeLibrary.decode(encoded.replace("\"memory\":true", "\"memory\":\"true\"")) }.isFailure)
        assertFalse(KnowledgeLibrary.failureMessage(Exception("secret document text or path")).contains("secret"))
        assertEquals("This fact is already saved.", KnowledgeLibrary.failureMessage(Exception("This fact is already saved.")))
    }

    @Test fun backupVersionTwoPreservesExclusionWithoutDroppingLegacySupport() {
        assertEquals(2, JarvisBackupPolicy.CURRENT_VERSION)
        assertTrue(JarvisBackupPolicy.isSupportedVersion(1))
        assertTrue(JarvisBackupPolicy.isSupportedVersion(2))
        listOf(-1, 0, 3, Int.MAX_VALUE).forEach { assertFalse(JarvisBackupPolicy.isSupportedVersion(it)) }
    }
}
