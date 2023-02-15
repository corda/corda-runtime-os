package net.corda.v5.crypto

import net.corda.v5.base.annotations.CordaSerializable
import net.corda.v5.base.types.ByteArrays.parseAsHex
import net.corda.v5.base.types.ByteArrays.toHexString
import net.corda.v5.base.types.OpaqueBytes
import java.nio.ByteBuffer

/**
 * Container for a cryptographically secure hash value.
 * Provides utilities for generating a cryptographic hash using different algorithms (currently only SHA-256 supported).
 *
 * @param algorithm Hashing algorithm which was used to generate the hash.
 * @param bytes Hash value.
 *
 * @property bytes Hash value
 */
@CordaSerializable
class SecureHash(
    /**
     * Hashing algorithm which was used to generate the hash.
     */
    val algorithm: String,
    bytes: ByteArray
) : OpaqueBytes(bytes) {

    companion object {
        const val DELIMITER = ':'

        /**
         * Creates a [SecureHash].
         *
         * This function does not validate the length of the created digest. Prefer using
         * [net.corda.v5.application.crypto.HashingService.parse] for a safer mechanism for creating [SecureHash]es.
         *
         * @see net.corda.v5.application.crypto.HashingService.parse
         */
        @JvmStatic
        fun parse(str: String): SecureHash {
            val idx = str.indexOf(DELIMITER)
            return if (idx == -1) {
                throw IllegalArgumentException("Provided string: $str should be of format algorithm:hexadecimal")
            } else {
                val algorithm = str.substring(0, idx)
                val value = str.substring(idx + 1)
                val data = parseAsHex(value)
                SecureHash(algorithm, data)
            }
        }
    }

    /**
     * Returns hexadecimal representation of the hash value.
     */
    fun toHexString(): String = toHexString(bytes)

    /**
     * Returns the first [prefixLen] hexadecimal digits of the [SecureHash] value.
     * @param prefixLen The number of characters in the prefix.
     */
    fun prefixChars(prefixLen: Int = 6) = toHexString().substring(0, prefixLen)

    /**
     * Compares the two given instances of the [SecureHash] based on the content.
     */
    override fun equals(other: Any?): Boolean {
        return when {
            this === other -> true
            other !is SecureHash -> false
            else -> algorithm == other.algorithm && super.equals(other)
        }
    }

    /**
     * Returns a hash code value for the object.
     */
    override fun hashCode() = ByteBuffer.wrap(bytes).int

    /**
     * Converts a [SecureHash] object to a string representation containing the [algorithm] and hexadecimal
     * representation of the [bytes] separated by the colon character.
     */
    override fun toString(): String {
        return "$algorithm$DELIMITER${toHexString()}"
    }
}