package com.example.myjarvice.data

import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.nio.ByteBuffer

class BackupProtectionTest {
    private val password = "twelve clear words!".toCharArray()
    private val archive = "PK synthetic archive with private text".toByteArray()

    private fun protected() = BackupProtection.encrypt(archive, password)
    private fun rejected(bytes: ByteArray, key: CharArray? = password): String {
        try { BackupProtection.open(bytes.inputStream(), key); fail("Unsafe input accepted") }
        catch (error: IllegalArgumentException) { return error.message.orEmpty() }
        error("Expected rejection")
    }

    @Test fun roundTripAndFreshRandomness() {
        val first = protected()
        val second = protected()
        assertTrue(BackupProtection.isProtected(first))
        assertFalse(first.contentEquals(second))
        assertArrayEquals(archive, BackupProtection.open(first.inputStream(), password))
        assertArrayEquals(archive, BackupProtection.open(second.inputStream(), password))
        assertFalse(first.toString(Charsets.ISO_8859_1).contains("private text"))
        assertArrayEquals("twelve clear words!".toCharArray(), password) // caller owns cleanup
    }

    @Test fun unicodeAndSpacesAreNotTrimmed() {
        val key = "  trees café 星 river  ".toCharArray()
        val bytes = BackupProtection.encrypt(archive, key)
        assertArrayEquals(archive, BackupProtection.open(bytes.inputStream(), key))
        rejected(bytes, "trees café 星 river".toCharArray())
    }

    @Test fun wrongPassphraseHasNoPlaintextOrSecretInError() {
        val error = rejected(protected(), "incorrect password".toCharArray())
        assertEquals("Wrong passphrase or damaged backup. Nothing was restored.", error)
        assertFalse(error.contains(String(password)))
        assertFalse(error.contains("private text"))
    }

    @Test fun tamperingWithSaltNoncePayloadOrTagFailsAuthentication() {
        val bytes = protected()
        for (index in listOf(13, 29, 41, bytes.lastIndex)) {
            val modified = bytes.copyOf()
            modified[index] = (modified[index].toInt() xor 1).toByte()
            assertEquals("Wrong passphrase or damaged backup. Nothing was restored.", rejected(modified))
        }
    }

    @Test fun untrustedWorkFactorAndVersionAreRejected() {
        val bytes = protected()
        val version = bytes.copyOf().also { it[8] = 2 }
        assertEquals("Protected backup format is not supported.", rejected(version))
        for (cost in listOf(0, 1, 599_999, Int.MAX_VALUE)) {
            val modified = bytes.copyOf()
            ByteBuffer.wrap(modified).putInt(9, cost)
            assertEquals("Protected backup format is not supported.", rejected(modified))
        }
    }

    @Test fun missingPassphraseTruncationAndAppendedBytesAreRejected() {
        val bytes = protected()
        rejected(bytes, null)
        rejected(bytes.copyOf(10))
        rejected(bytes.copyOf(bytes.size - 1))
        rejected(bytes + byteArrayOf(1))
    }

    @Test fun legacyArchivesRemainReadableWithoutPassword() {
        assertArrayEquals(archive, BackupProtection.open(archive.inputStream()))
        assertFalse(BackupProtection.requiresPassphrase(archive.inputStream()))
        rejected(archive, password)
    }

    @Test fun detectionWorksWithShortReads() {
        val bytes = protected()
        val shortReads = object : ByteArrayInputStream(bytes) {
            override fun read(b: ByteArray, off: Int, len: Int) = super.read(b, off, minOf(len, 1))
        }
        assertTrue(BackupProtection.requiresPassphrase(shortReads))
        assertFalse(BackupProtection.requiresPassphrase(bytes.copyOf(7).inputStream()))
    }

    @Test fun oversizedSourceIsBoundedBeforeCryptoOrZipParsing() {
        var remaining = BackupProtection.MAX_FILE_BYTES + 1
        val oversized = object : InputStream() {
            override fun read(): Int = if (remaining-- > 0) 0 else -1
            override fun read(b: ByteArray, off: Int, len: Int): Int {
                if (remaining <= 0) return -1
                val count = minOf(len, remaining)
                b.fill(0, off, off + count); remaining -= count
                return count
            }
        }
        try { BackupProtection.open(oversized); fail("Accepted oversized input") }
        catch (error: IllegalArgumentException) { assertEquals("Backup exceeds its file size limit.", error.message) }
    }

    @Test fun passphrasePolicyRejectsShortBlankOrExcessiveValues() {
        assertNotNull(BackupProtection.passphraseError("short"))
        assertNotNull(BackupProtection.passphraseError(" ".repeat(12)))
        assertNotNull(BackupProtection.passphraseError("a".repeat(129)))
        assertNull(BackupProtection.passphraseError("four separate words now"))
        for (key in listOf("short", " ".repeat(12), "a".repeat(129))) {
            try { BackupProtection.encrypt(archive, key.toCharArray()); fail("Invalid passphrase accepted") }
            catch (_: IllegalArgumentException) { }
        }
    }
}
