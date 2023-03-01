package net.corda.rest.test.utils

import java.io.InputStream
import java.security.MessageDigest

object ChecksumUtil {
    private const val HASH_ALGORITHM = "SHA-256"

    fun generateChecksum(input: InputStream): String {
        input.use {
            val messageDigest = MessageDigest.getInstance(HASH_ALGORITHM)
            val bytes = messageDigest.digest(input.readAllBytes())
            return bytes.joinToString(separator = "", limit = 40) { Integer.toHexString(it.toInt()) }
        }
    }
}