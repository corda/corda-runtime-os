package net.corda.v5.crypto

import net.corda.v5.base.types.toHexString
import java.security.PublicKey

fun PublicKey.calculateHash() = PublicKeyHash.calculate(this)

/**
 * Container for a public key hash value.
 * Provides utilities for generating a public key hash by calculating it from a given [PublicKey], or by parsing a given
 * [String] or [ByteArray] input.
 */
class PublicKeyHash private constructor(
    val value: String
) {
    companion object {
        /**
         * Parses the given hash as [ByteArray] and wraps it in a [PublicKeyHash].
         *
         * @param bytes The byte array to be parsed.
         * @return Returns a [PublicKeyHash] containing the SHA-256 hash.
         * @throws IllegalArgumentException if size of [bytes] is not 32.
         */
        fun parse(bytes: ByteArray): PublicKeyHash {
            require(bytes.size == 32) {
                "Input must be 32 bytes long for SHA-256 hash."
            }
            return PublicKeyHash(value = bytes.toHexString())
        }

        /**
         * Parses the given hash as hexadecimal [String] and wraps it in a [PublicKeyHash].
         *
         * @param str The Hex string to be parsed.
         * @return Returns a [PublicKeyHash] containing the SHA-256 hash.
         * @throws IllegalArgumentException if length of [str] is not 64, or if [str] is not a valid Hex string.
         */
        fun parse(str: String): PublicKeyHash {
            require(str.length == 64) {
                "Input must be 64 characters long for Hex of SHA-256 hash."
            }
            require(str.all { (it in '0'..'9') || (it in 'A'..'F') || (it in 'a'..'f') }) {
                "Input is an invalid Hex string."
            }
            return PublicKeyHash(value = str.toUpperCase())
        }

        /**
         * Computes the public key hash from a given [PublicKey].
         *
         * @param publicKey The public key whose hash is to be generated.
         * @return Returns a [PublicKeyHash] containing the SHA-256 hash.
         */
        fun calculate(publicKey: PublicKey): PublicKeyHash =
            PublicKeyHash(value = publicKey.sha256Bytes().toHexString())
    }

    override fun hashCode() = value.hashCode()

    override fun equals(other: Any?): Boolean {
        return when (other) {
            is PublicKeyHash -> value == other.value
            is ByteArray -> value == other.toHexString()
            is String -> value.equals(other, true)
            else -> false
        }
    }

    /**
     * Converts a [PublicKeyHash] object to a string representation. Returns Hex representation of its hash value.
     */
    override fun toString() = value
}
