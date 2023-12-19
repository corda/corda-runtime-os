package net.corda.crypto.client

import net.corda.lifecycle.Lifecycle

/**
 * Encryption operations client to encrypt and decrypt session data for Link Manager.
 */
interface SessionEncryptionOpsClient : Lifecycle {
    /**
     * Encrypt [plainBytes] using the symmetric key associated with the cluster-level tenant 'p2p'.
     *
     * If the key is rotated, the ability to decrypt any data previously encrypted using that key will be lost.
     *
     * @param plainBytes The byte array to be encrypted.
     * @param alias Optional. Alias of the symmetric key. If no alias is provided, the default alias for 'p2p' under
     * HSM category 'ENCRYPTION_SECRET' will be used.
     */
    fun encryptSessionData(
        plainBytes: ByteArray,
        alias: String? = null,
    ): ByteArray

    /**
     * Decrypt [cipherBytes] using the symmetric key associated with the cluster-level tenant 'p2p'.
     *
     * @param cipherBytes The byte array to be decrypted.
     * @param alias Optional. Alias of the symmetric key. If no alias is provided, the default alias for 'p2p' under
     * HSM category 'ENCRYPTION_SECRET' will be used.
     */
    fun decryptSessionData(
        cipherBytes: ByteArray,
        alias: String? = null,
    ): ByteArray
}
