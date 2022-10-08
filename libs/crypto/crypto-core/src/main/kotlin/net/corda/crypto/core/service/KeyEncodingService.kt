package net.corda.crypto.core.service

import java.security.PublicKey

/**
 * Encoding service which can encode and decode keys to or from byte arrays or strings using PEM encoding format.
 *
 * Decoding would be slow in case of the several handlers as it'll try to use each sequentially until one succeed or all
 * fail.
 */
interface KeyEncodingService {
    /**
     * Decodes public key from byte array.
     *
     * @throws IllegalArgumentException if the key scheme is not supported.
     * @throws net.corda.v5.crypto.exceptions.CryptoException for general cryptographic exceptions.
     */
    fun decode(encodedKey: ByteArray): PublicKey

    /**
     * Decodes public key from PEM encoded string.
     *
     * @throws IllegalArgumentException if the key scheme is not supported.
     * @throws net.corda.v5.crypto.exceptions.CryptoException for general cryptographic exceptions.
     */
    fun decodePem(encodedKey: String): PublicKey

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
    fun encodeAsPem(publicKey: PublicKey): String
}