package com.example.myjarvice.data

import android.database.MatrixCursor
import android.net.Uri
import android.provider.ContactsContract
import org.junit.Assert.*
import org.junit.Test

/** Only injected cursors: never reads, writes or requests access to real phone contacts. */
class DraftContactRepositoryTest {
    @Test fun readsOnlyNameAtExactlyTheSelectedContactAndClosesCursor() {
        val uri = Uri.parse("content://com.android.contacts/contacts/lookup/synthetic/42")
        var reads = 0
        lateinit var cursor: MatrixCursor
        val repository = DraftContactRepository { received, columns ->
            reads++
            assertEquals(uri, received)
            assertArrayEquals(arrayOf(ContactsContract.Contacts.DISPLAY_NAME_PRIMARY), columns)
            MatrixCursor(columns).apply { addRow(arrayOf("  Synthetic\nAlex  ")) }.also { cursor = it }
        }
        assertEquals("Synthetic Alex", repository.selectedName(uri))
        assertEquals(1, reads)
        assertTrue(cursor.isClosed)
    }

    @Test fun rejectsCollectionsForeignUrisQueriesAndDataRowsBeforeAnyRead() {
        var reads = 0
        val repository = DraftContactRepository { _, _ -> reads++; null }
        val rejected = listOf(
            "content://com.android.contacts/contacts", "content://com.android.contacts/contacts/0",
            "content://com.android.contacts/contacts/no-id", "content://com.android.contacts/data/42",
            "content://foreign/contacts/42", "https://com.android.contacts/contacts/42",
            "content://com.android.contacts/contacts/42?selection=all",
            "content://com.android.contacts/contacts/42#fragment",
            "content://com.android.contacts/contacts/lookup/synthetic/42/extra",
            "content://com.android.contacts/contacts/lookup/" + "x".repeat(2048)
        )
        rejected.forEach { raw ->
            try { repository.selectedName(Uri.parse(raw)); fail("Accepted $raw") }
            catch (_: IllegalArgumentException) { }
        }
        assertEquals(0, reads)
    }

    @Test fun missingBlankOrAmbiguousContactFailsWithoutFallbackAndClosesCursor() {
        val uri = Uri.parse("content://com.android.contacts/contacts/42")
        for (rows in listOf(emptyList(), listOf(null), listOf("  "), listOf("Alex", "Sam"))) {
            lateinit var cursor: MatrixCursor
            var reads = 0
            val repository = DraftContactRepository { _, columns ->
                reads++
                MatrixCursor(columns).apply { rows.forEach { addRow(arrayOf(it)) } }.also { cursor = it }
            }
            try { repository.selectedName(uri); fail("Unreadable contact accepted") }
            catch (_: IllegalStateException) { }
            assertEquals(1, reads)
            assertTrue(cursor.isClosed)
        }
    }

    @Test fun providerFailuresAreNotRetriedAsAnAddressBookQuery() {
        val uri = Uri.parse("content://com.android.contacts/contacts/lookup/synthetic")
        var reads = 0
        val denied = DraftContactRepository { _, _ -> reads++; throw SecurityException("Synthetic denial") }
        try { denied.selectedName(uri); fail("Denial ignored") } catch (_: SecurityException) { }
        assertEquals(1, reads)
        val absent = DraftContactRepository { _, _ -> null }
        try { absent.selectedName(uri); fail("Null cursor accepted") } catch (_: IllegalStateException) { }
    }
}
