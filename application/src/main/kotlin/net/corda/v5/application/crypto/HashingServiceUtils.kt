@file:JvmName("HashingServiceUtils")

package net.corda.v5.application.crypto

import net.corda.v5.base.types.parseAsHex
import net.corda.v5.crypto.DigestAlgorithmName
import net.corda.v5.crypto.SecureHash

/**
 * Converts a digest represented as a {algorithm:}hexadecimal [String] into a [SecureHash].
 *
 * This function validates the length of the created digest. Prefer the usage of this function over [SecureHash.parse].
 *
 * @param str The algorithm name followed by a delimiter and the sequence of hexadecimal digits that represents a digest.
 *
 * @throws IllegalArgumentException The input string does not contain the expected number of hexadecimal digits, or it contains
 * incorrectly-encoded characters.
 *
 * @see SecureHash.parse
 */
@Suppress("TooGenericExceptionCaught")
fun HashingService.parse(str: String): SecureHash {
    val idx = str.indexOf(SecureHash.DELIMITER)
    return if (idx == -1) {
        throw IllegalArgumentException("Provided string should be of format algorithm:hexadecimal")
    } else {
        val algorithm = str.substring(0, idx)
        val value = str.substring(idx + 1)
        try {
            decode(value, DigestAlgorithmName((algorithm)))
        } catch (e: Throwable) {
            throw IllegalArgumentException("Failed to create ${SecureHash::class.simpleName}: ${e.message}", e)
        }
    }
}

/**
 * Converts a digest represented as a hexadecimal [String] and a digest algorithm into a [SecureHash].
 */
private fun HashingService.decode(value: String, digestAlgorithmName: DigestAlgorithmName): SecureHash {
    val digestLength = digestLength(digestAlgorithmName)
    val data = value.parseAsHex()
    return when (data.size) {
        digestLength -> SecureHash(digestAlgorithmName.name, data)
        else -> throw IllegalArgumentException("Provided string is ${data.size} bytes not $digestLength bytes in hex: $value")
    }
}