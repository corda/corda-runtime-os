package net.corda.crypto.core

import javax.crypto.SecretKey

/**
 * Supports wrapping/unwrapping operations for secrets [ManagedSecret] and other keys [ManagedKey]
 */
interface ManagedKey : SecretKey {
    /**
     * Instance of [Encryptor] which supports encryption using this key (AES algorithm with
     * the key length of 256)
     */
    val encryptor: Encryptor

    /**
     * Encrypts (or wraps) the '[other]' [ManagedKey].
     *
     * @return [ByteArray] which represents `[other]` [ManagedKey].
     *
     * The [ManagedKey] can be restored from [ByteArray] by using `[unwrapKey]` method.
     */
    fun wrapKey(other: ManagedKey): ByteArray

    /**
     * Decrypts (or unwraps) the '[other]' to [ManagedKey].
     */
    fun unwrapKey(other: ByteArray): ManagedKey

    /**
     * Encrypts (or wraps) the [ManagedSecret].
     *
     * @return [ByteArray] which represents '[secret]' [ManagedSecret].
     *
     * The [ManagedSecret] can be restored from [ByteArray] by using `[unwrapSecret]` method.
     */
    fun wrapSecret(secret: ManagedSecret): ByteArray

    /**
     * Decrypts (or unwraps) the '[secret]' to  [ManagedSecret].
     */
    fun unwrapSecret(secret: ByteArray): ManagedSecret
}