package net.corda.crypto.core

/**
 * Supports decryption operations.
 */
interface Decryptor {
    /**
     * Decrypts the given byte array.
     */
    fun decrypt(cipherText: ByteArray): ByteArray
}