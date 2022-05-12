package net.corda.crypto.testkit

import net.corda.v5.crypto.SecureHash
import java.security.MessageDigest

object SecureHashUtils {
    fun randomSecureHash(): SecureHash {
        val allowedChars = '0'..'9'
        val randomBytes = (1..16).map { allowedChars.random() }.joinToString("").toByteArray()
        val digest = MessageDigest.getInstance("SHA-256")
        return SecureHash(digest.algorithm, digest.digest(randomBytes))
    }
}
