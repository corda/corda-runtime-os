package net.corda.v5.cipher.suite

/**
 * Crypto service which can encrypt and decrypt key used by SOFT HSM.
 * The key will have to be created outside the scope of the service.
 */
interface SoftKeyWrappingService {
    /**
     * Encrypts a byte array using the key identified by the alias.
     *
     * @param alias the key alias which must be used for encryption.
     * @param clearText the data to be encrypted.
     * @param context the optional key/value operation context. The context will have at least one variable defined -
     * 'tenantId'.
     *
     * @return the encrypted data.
     *
     * @throws IllegalArgumentException if the key is not found or in general the input parameters are wrong
     * @throws net.corda.v5.crypto.exceptions.CryptoException, non-recoverable
     */
    fun wrap(alias: String, clearText: ByteArray, context: Map<String, String>): ByteArray

    /**
     * Decrypt a byte array using the key identified by the alias.
     *
     * @param alias the key alias which must be used for encryption.
     * @param cipherText the encrypted to be decrypted.
     * @param context the optional key/value operation context. The context will have at least one variable defined -
     * 'tenantId'.
     *
     * @return the clear text data.
     *
     * @throws IllegalArgumentException if the key is not found or in general the input parameters are wrong
     * @throws net.corda.v5.crypto.exceptions.CryptoException, non-recoverable
     */
    fun unwrap(alias: String, cipherText: ByteArray, context: Map<String, String>): ByteArray
}