package net.corda.crypto.poc.ciphersuite2.platform

import java.security.PublicKey

interface KeyEncodingService2 {
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
}