package net.corda.chunking.impl

import net.corda.v5.crypto.SecureHash
import java.nio.file.Files
import java.nio.file.Path
import java.security.DigestInputStream
import java.security.MessageDigest

/**
 * Entirely internal to this lib.
 *
 * Stream that is chunked and written, is calculated via [DigestInputStream], but reconstructed
 * file at [Path] is calculated via [digestForPath] below.
 */
internal class InternalChecksum {
    companion object {
        const val ALGORITHM = "SHA-256"
        fun getMessageDigest(): MessageDigest = MessageDigest.getInstance(ALGORITHM)
    }

    private val messageDigest = getMessageDigest()

    /**
     * Return the checksum for some file on some [Path]
     */
    fun digestForPath(path: Path): SecureHash {
        messageDigest.reset()
        DigestInputStream(Files.newInputStream(path), messageDigest).use { inputStream ->
            val bytes = ByteArray(1024 * 1024)
            @Suppress("EmptyWhileBlock")
            while (inputStream.read(bytes) != -1) {
            }
            return SecureHash(ALGORITHM, messageDigest.digest())
        }
    }
}