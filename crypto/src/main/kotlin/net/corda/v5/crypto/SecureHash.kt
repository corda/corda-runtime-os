package net.corda.v5.crypto

import net.corda.v5.base.annotations.CordaSerializable
import net.corda.v5.base.types.OpaqueBytes
import net.corda.v5.base.types.parseAsHex
import net.corda.v5.base.types.toHexString
import java.nio.ByteBuffer

/**
 * Container for a cryptographically secure hash value.
 * Provides utilities for generating a cryptographic hash using different algorithms (currently only SHA-256 supported).
 */
@CordaSerializable
class SecureHash(val algorithm: String, bytes: ByteArray) : OpaqueBytes(bytes) {

    companion object {
        const val DELIMITER = ':'

        /**
         * Creates a [SecureHash].
         *
         * This function does not validate the length of the created digest. Prefer using [DigestService.create] for a safer mechanism
         * for creating [SecureHash]es.
         *
         * @see DigestService.create
         */
        @JvmStatic
        fun create(str: String): SecureHash {
            val idx = str.indexOf(DELIMITER)
            return if (idx == -1) {
                throw IllegalArgumentException("Provided string: $str should be of format algorithm:hexadecimal")
            } else {
                val algorithm = str.substring(0, idx)
                val value = str.substring(idx + 1)
                val data = value.parseAsHex()
                SecureHash(algorithm, data)
            }
        }
    }

    fun toHexString(): String = bytes.toHexString()

    /**
     * Returns the first [prefixLen] hexadecimal digits of the [SecureHash] value.
     * @param prefixLen The number of characters in the prefix.
     */
    fun prefixChars(prefixLen: Int = 6) = toHexString().substring(0, prefixLen)

    override fun equals(other: Any?): Boolean {
        return when {
            this === other -> true
            other !is SecureHash -> false
            else -> algorithm == other.algorithm && super.equals(other)
        }
    }

    override fun hashCode() = ByteBuffer.wrap(bytes).int

    override fun toString(): String {
        return "$algorithm$DELIMITER${toHexString()}"
    }
}