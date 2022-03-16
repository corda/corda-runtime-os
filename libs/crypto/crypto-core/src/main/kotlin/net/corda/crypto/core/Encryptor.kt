package net.corda.crypto.core

/**
 * Supports encryption operations.
 */
interface Encryptor {
    /**
     * Encrypts the given byte array.
     */
    fun encrypt(plainText: ByteArray): ByteArray
}