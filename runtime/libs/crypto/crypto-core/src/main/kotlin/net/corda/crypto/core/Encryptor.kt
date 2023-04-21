package net.corda.crypto.core

/**
 * Supports encryption operations.
 */
interface Encryptor {
    /**
     * Encrypts the given byte array.
     */
    fun encrypt(plainText: ByteArray): ByteArray

    /**
     * Decrypts the given byte array.
     */
    fun decrypt(cipherText: ByteArray): ByteArray}