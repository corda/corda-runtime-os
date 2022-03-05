package net.corda.v5.cipher.suite

import net.corda.v5.crypto.exceptions.CryptoServiceLibraryException
import java.security.PublicKey

/**
 * Encoding service which can encode and decode keys to or from byte arrays
 * or strings using PEM encoding format.
 */
interface KeyEncodingService {
    /**
     * Decodes public key from byte array.
     *
     * @throws [CryptoServiceLibraryException] for general cryptographic exceptions.
     */
    fun decodePublicKey(encodedKey: ByteArray): PublicKey

    /**
     * Decodes public key from PEM encoded string.
     *
     * @throws [CryptoServiceLibraryException] for general cryptographic exceptions.
     */
    fun decodePublicKey(encodedKey: String): PublicKey

    /**
     * Encodes public key to byte array.
     * The default implementation returns the [PublicKey.getEncoded]
     *
     * @throws [CryptoServiceLibraryException] for general cryptographic exceptions.
     */
    fun encodeAsByteArray(publicKey: PublicKey): ByteArray = publicKey.encoded

    /**
     * Encodes public key to PEM encoded string.
     *
     * @throws [CryptoServiceLibraryException] for general cryptographic exceptions.
     */
    fun encodeAsString(publicKey: PublicKey): String

    /**
     * Convert a public key to a supported implementation. This can be used to convert a SUN's EC key to an BC key.
     *
     * @throws IllegalArgumentException on not supported scheme or if the given key specification
     * is inappropriate for a supported key factory to produce a private key.
     */
    fun toSupportedPublicKey(key: PublicKey): PublicKey
}