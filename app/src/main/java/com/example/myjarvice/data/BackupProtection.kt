package com.example.myjarvice.data

import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.nio.ByteBuffer
import java.security.SecureRandom
import javax.crypto.BadPaddingException
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/** Versioned, authenticated envelope around the existing ZIP; no custom cryptographic primitives.
 * PBKDF2WithHmacSHA256 is available on Android 26+. Never fall back to weaker encryption.
 */
internal object BackupProtection {
    class AuthenticationFailure : IllegalArgumentException("Wrong passphrase or damaged backup. Nothing was restored.")
    private val magic = "JRVCRYPT".toByteArray(Charsets.US_ASCII)
    private const val VERSION: Byte = 1
    private const val ITERATIONS = 600_000
    private const val SALT_BYTES = 16
    private const val NONCE_BYTES = 12
    private const val TAG_BYTES = 16
    private const val HEADER_BYTES = 8 + 1 + 4 + SALT_BYTES + NONCE_BYTES
    const val MAX_ZIP_BYTES = 34 * 1024 * 1024
    const val MAX_FILE_BYTES = MAX_ZIP_BYTES + HEADER_BYTES + TAG_BYTES

    fun passphraseError(value: String): String? = when {
        value.length < 12 -> "Use at least 12 characters, ideally several unrelated words."
        value.length > 128 -> "Use no more than 128 characters."
        value.isBlank() -> "The passphrase cannot contain only spaces."
        else -> null
    }

    fun isProtected(prefix: ByteArray): Boolean = prefix.size >= magic.size &&
        magic.indices.all { prefix[it] == magic[it] }

    fun requiresPassphrase(source: InputStream): Boolean {
        val prefix = ByteArray(magic.size)
        var offset = 0
        while (offset < prefix.size) {
            val read = source.read(prefix, offset, prefix.size - offset)
            if (read < 0) break
            if (read == 0) continue
            offset += read
        }
        return offset == prefix.size && isProtected(prefix)
    }

    fun encrypt(zip: ByteArray, passphrase: CharArray): ByteArray {
        require(zip.isNotEmpty() && zip.size <= MAX_ZIP_BYTES) { "Backup exceeds its file size limit." }
        require(passphrase.size in 12..128 && passphrase.any { !it.isWhitespace() }) { "Use a passphrase of 12–128 characters, not only spaces." }
        val random = SecureRandom()
        val salt = ByteArray(SALT_BYTES).also(random::nextBytes)
        val nonce = ByteArray(NONCE_BYTES).also(random::nextBytes)
        val header = ByteBuffer.allocate(HEADER_BYTES).put(magic).put(VERSION)
            .putInt(ITERATIONS).put(salt).put(nonce).array()
        return withKey(passphrase, salt) { key ->
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(TAG_BYTES * 8, nonce))
            cipher.updateAAD(header)
            header + cipher.doFinal(zip)
        }
    }

    /** Reads a bounded file. Authentication finishes before any ZIP parsing or restore writes. */
    fun open(source: InputStream, passphrase: CharArray? = null): ByteArray {
        val bytes = readBounded(source)
        if (!isProtected(bytes)) {
            require(bytes.size <= MAX_ZIP_BYTES) { "Backup exceeds its file size limit." }
            require(passphrase == null) { "This file is not a protected Jarvis backup." }
            return bytes
        }
        try {
            require(bytes.size >= HEADER_BYTES + TAG_BYTES) { "Protected backup is incomplete." }
            val header = bytes.copyOfRange(0, HEADER_BYTES)
            val fields = ByteBuffer.wrap(header).apply { position(magic.size) }
            require(fields.get() == VERSION && fields.int == ITERATIONS) { "Protected backup format is not supported." }
            val salt = ByteArray(SALT_BYTES).also(fields::get)
            val nonce = ByteArray(NONCE_BYTES).also(fields::get)
            require(passphrase != null && passphrase.size in 12..128) { "Enter the backup passphrase (12–128 characters)." }
            return withKey(passphrase, salt) { key ->
                val cipher = Cipher.getInstance("AES/GCM/NoPadding")
                cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(TAG_BYTES * 8, nonce))
                cipher.updateAAD(header)
                try {
                    cipher.doFinal(bytes, HEADER_BYTES, bytes.size - HEADER_BYTES)
                } catch (_: BadPaddingException) {
                    throw AuthenticationFailure()
                }
            }
        } finally { bytes.fill(0) }
    }

    private fun <T> withKey(passphrase: CharArray, salt: ByteArray, block: (SecretKeySpec) -> T): T {
        val spec = PBEKeySpec(passphrase, salt, ITERATIONS, 256)
        val keyBytes = try { SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded }
            finally { spec.clearPassword() }
        return try { block(SecretKeySpec(keyBytes, "AES")) } finally { keyBytes.fill(0) }
    }

    private fun readBounded(source: InputStream): ByteArray {
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        while (true) {
            val read = source.read(buffer)
            if (read < 0) break
            if (read == 0) continue
            require(output.size() + read <= MAX_FILE_BYTES) { "Backup exceeds its file size limit." }
            output.write(buffer, 0, read)
        }
        return output.toByteArray()
    }
}
