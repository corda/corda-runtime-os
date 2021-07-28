@file:JvmName("DigestServiceUtils")

package net.corda.v5.crypto

import net.corda.v5.base.types.parseAsHex
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentMap

private val hashConstants: ConcurrentMap<String, HashConstants> = ConcurrentHashMap()

/**
 * Converts a digest represented as a {algorithm:}hexadecimal [String] into a [SecureHash].
 *
 * This function validates the length of the created digest. Prefer the usage of this function over [SecureHash.create].
 *
 * @param str The algorithm name followed by a delimiter and the sequence of hexadecimal digits that represents a digest.
 *
 * @throws IllegalArgumentException The input string does not contain the expected number of hexadecimal digits, or it contains
 * incorrectly-encoded characters.
 *
 * @see SecureHash.create
 */
@Suppress("TooGenericExceptionCaught")
fun DigestService.create(str: String): SecureHash {
    val idx = str.indexOf(SecureHash.DELIMITER)
    return if (idx == -1) {
        throw IllegalArgumentException("Provided string should be of format algorithm:hexadecimal")
    } else {
        val algorithm = str.substring(0, idx)
        val value = str.substring(idx + 1)
        try {
            decode(value, DigestAlgorithmName((algorithm)))
        } catch (e: Exception) {
            throw IllegalArgumentException("Failed to create ${SecureHash::class.simpleName}: ${e.message}", e)
        }
    }
}

/**
 * Returns a [SecureHash] zero constant of [SecureHash.bytes] length equal to [DigestAlgorithmName] digest length in bytes, with all of
 * its bytes being 0x00.
 *
 * @param digestAlgorithmName The digest algorithm to get the zero constant for.
 */
fun DigestService.getZeroHash(digestAlgorithmName: DigestAlgorithmName): SecureHash {
    return getConstantsFor(digestAlgorithmName).zero
}

/**
 * Returns a [SecureHash] all ones constant of [SecureHash.bytes] length equal to [DigestAlgorithmName] digest length in bytes, with all
 * of its bytes being 0xFF.
 *
 * @param digestAlgorithmName The digest algorithm to get the all ones constant for.
 */
fun DigestService.getAllOnesHash(digestAlgorithmName: DigestAlgorithmName): SecureHash {
    return getConstantsFor(digestAlgorithmName).allOnes
}

private fun DigestService.getConstantsFor(digestAlgorithmName: DigestAlgorithmName): HashConstants {
    val algorithm = digestAlgorithmName.name
    return hashConstants.getOrPut(algorithm) {
        val digestLength = digestLength(digestAlgorithmName)
        HashConstants(
            zero = SecureHash(algorithm, ByteArray(digestLength) { 0.toByte() }),
            allOnes = SecureHash(algorithm, ByteArray(digestLength) { 255.toByte() })
        )
    }
}

/**
 * Converts a digest represented as a hexadecimal [String] and a digest algorithm into a [SecureHash].
 */
private fun DigestService.decode(value: String, digestAlgorithmName: DigestAlgorithmName): SecureHash {
    val digestLength = digestLength(digestAlgorithmName)
    val data = value.parseAsHex()
    return when (data.size) {
        digestLength -> SecureHash(digestAlgorithmName.name, data)
        else -> throw IllegalArgumentException("Provided string is ${data.size} bytes not $digestLength bytes in hex: $value")
    }
}

private class HashConstants(val zero: SecureHash, val allOnes: SecureHash)
