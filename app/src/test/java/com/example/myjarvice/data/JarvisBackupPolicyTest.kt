package com.example.myjarvice.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class JarvisBackupPolicyTest {
    @Test fun previewCountsOnlyRestorableItems() {
        val preview = BackupPreview(
            createdAt = 1L,
            conversations = 3,
            memories = 2,
            documents = 1,
            savedItems = 4,
            tasks = 5,
            mediaFiles = 2
        )
        assertEquals(15, preview.totalItems)
    }

    @Test fun mediaEntryMustStayInsideTheArchiveMediaFolder() {
        assertTrue(JarvisBackupPolicy.isSafeMediaEntry("media/abc-123.jpg"))
        assertFalse(JarvisBackupPolicy.isSafeMediaEntry("../settings.xml"))
        assertFalse(JarvisBackupPolicy.isSafeMediaEntry("media/../../settings.xml"))
        assertFalse(JarvisBackupPolicy.isSafeMediaEntry("manifest.json"))
    }
}
