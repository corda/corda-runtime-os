package net.corda.crypto.testkit

import net.corda.crypto.core.SecureHashImpl
import net.corda.v5.crypto.SecureHash
import java.security.MessageDigest

object SecureHashUtils {

    /**
     * Returns a random set of bytes
     */
    fun randomBytes(): ByteArray {
        return (1..16).map { ('0'..'9').random() }.joinToString("").toByteArray()
    }

    /**
     * Returns a random secure hash of the specified algorithm
     */
    fun randomSecureHash(algorithm: String = "SHA-256"): SecureHash {
        val digest = MessageDigest.getInstance(algorithm)
        return SecureHashImpl(digest.algorithm, digest.digest(randomBytes()))
    }
}
