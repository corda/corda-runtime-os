package net.corda.v5.cipher.suite

import java.security.PublicKey

/**
 * Encoding service which can encode and decode keys to or from byte arrays or strings using PEM encoding format.
 */
interface KeyEncodingService {
    /**
     * Decodes public key from byte array.
     *
     * @throws IllegalArgumentException if the key scheme is not supported.
     * @throws net.corda.v5.crypto.exceptions.CryptoException for general cryptographic exceptions.
     */
    fun decodePublicKey(encodedKey: ByteArray): PublicKey

    /**
     * Decodes public key from PEM encoded string.
     *
     * @throws IllegalArgumentException if the key scheme is not supported.
     * @throws net.corda.v5.crypto.exceptions.CryptoException for general cryptographic exceptions.
     */
    fun decodePublicKey(encodedKey: String): PublicKey

    /**
     * Encodes public key to byte array.
     * The default implementation returns the [PublicKey.getEncoded]
     *
     * @throws IllegalArgumentException if the key scheme is not supported.
     * @throws net.corda.v5.crypto.exceptions.CryptoException for general cryptographic exceptions.
     */
    fun encodeAsByteArray(publicKey: PublicKey): ByteArray = publicKey.encoded

    /**
     * Encodes public key to PEM encoded string.
     *
     * @throws IllegalArgumentException if the key scheme is not supported.
     * @throws net.corda.v5.crypto.exceptions.CryptoException for general cryptographic exceptions.
     */
    fun encodeAsString(publicKey: PublicKey): String

    /**
     * Converts a public key to a supported implementation. This can be used to convert a SUN's EC key to an BC key.
     *
     * @throws IllegalArgumentException if the key scheme is not supported.
     * @throws net.corda.v5.crypto.exceptions.CryptoException for general cryptographic exceptions.
     */
    fun toSupportedPublicKey(key: PublicKey): PublicKey
}