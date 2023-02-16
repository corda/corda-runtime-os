package net.corda.chunking

import java.nio.file.Files
import java.nio.file.Path
import java.security.DigestInputStream
import java.security.MessageDigest
import net.corda.chunking.Checksum.digestForPath
import net.corda.v5.crypto.SecureHash

/**
 * Stream that is chunked and written, is calculated via [DigestInputStream], but reconstructed
 * file at [Path] is calculated via [digestForPath] below.
 */
object Checksum {
    const val ALGORITHM = "SHA-256"
    fun newMessageDigest(): MessageDigest = MessageDigest.getInstance(ALGORITHM)

    /**
     * Return the checksum for some file on some [Path]
     */
    fun digestForPath(path: Path): SecureHash {
        val messageDigest = newMessageDigest()
        DigestInputStream(Files.newInputStream(path), messageDigest).use { inputStream ->
            val bytes = ByteArray(1024 * 1024)
            @Suppress("EmptyWhileBlock")
            while (inputStream.read(bytes) != -1) {
            }
            return SecureHash(ALGORITHM, messageDigest.digest())
        }
    }

    /**
     * Generate a checksum for some [bytes]
     */
    fun digestForBytes(bytes: ByteArray): SecureHash {
        val digest = newMessageDigest()
        digest.update(bytes)
        return SecureHash(ALGORITHM, digest.digest())
    }
}
