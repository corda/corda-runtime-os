@file:JvmName("PublicKeyUtils")
package net.corda.crypto.cipher.suite

import net.corda.v5.base.types.ByteArrays.toHexString
import net.corda.v5.crypto.sha256Bytes
import java.security.PublicKey

/**
 * Computes the public key hash from a given [PublicKey].
 */
fun PublicKey.calculateHash() =
    PublicKeyHash.calculate(this)

/**
 * Returns the id as the first 12 characters of an SHA-256 hash from a given [PublicKey].
 */
fun PublicKey.publicKeyId(): String =
    PublicKeyHash.calculate(this).id

/**
 * Container for a public key hash value.
 * Provides utilities for generating a public key hash by calculating it from a given [PublicKey], or by parsing a given
 * [String] or [ByteArray] input.
 *
 * @property value Hexadecimal string representing SHA-256 of the public key.
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
        @JvmStatic
        fun parse(bytes: ByteArray): PublicKeyHash {
            require(bytes.size == 32) {
                "Input must be 32 bytes long for SHA-256 hash."
            }
            return PublicKeyHash(value = toHexString(bytes))
        }

        /**
         * Parses the given hash as hexadecimal [String] and wraps it in a [PublicKeyHash].
         *
         * @param str The Hex string to be parsed.
         * @return Returns a [PublicKeyHash] containing the SHA-256 hash.
         * @throws IllegalArgumentException if length of [str] is not 64, or if [str] is not a valid Hex string.
         */
        @JvmStatic
        fun parse(str: String): PublicKeyHash {
            require(str.length == 64) {
                "Input must be 64 characters long for Hex of SHA-256 hash."
            }
            require(str.all { (it in '0'..'9') || (it in 'A'..'F') || (it in 'a'..'f') }) {
                "Input is an invalid Hex string."
            }
            return PublicKeyHash(value = str.uppercase())
        }

        /**
         * Computes the public key hash from a given [PublicKey].
         *
         * @param publicKey The public key whose hash is to be generated.
         * @return Returns a [PublicKeyHash] containing the SHA-256 hash.
         */
        @JvmStatic
        fun calculate(publicKey: PublicKey): PublicKeyHash =
            PublicKeyHash(value = toHexString(publicKey.sha256Bytes()))

        /**
         * Computes the public key hash from a given encoded [PublicKey].
         *
         * @param publicKey The public key whose hash is to be generated.
         * @return Returns a [PublicKeyHash] containing the SHA-256 hash.
         */
        @JvmStatic
        fun calculate(publicKey: ByteArray): PublicKeyHash =
            PublicKeyHash(value = toHexString(publicKey.sha256Bytes()))
    }

    /**
     * Returns the id as the first 12 characters of the [value] which is SHA-256 hash of the public key.
     */
    val id: String by lazy(LazyThreadSafetyMode.PUBLICATION) { value.substring(0, 12) }

    /**
     * Returns a hash code value for the object.
     */
    override fun hashCode() = value.hashCode()

    /**
     * Compares the object with the given instance of the [PublicKeyHash], [ByteArray], or [String] by converting the
     * object hexadecimal representation.
     */
    override fun equals(other: Any?): Boolean {
        return when (other) {
            is PublicKeyHash -> value == other.value
            is ByteArray -> value == toHexString(other)
            is String -> value.equals(other, true)
            else -> false
        }
    }

    /**
     * Converts a [PublicKeyHash] object to a string representation. Returns Hex representation of its hash value.
     */
    override fun toString() = value
}
