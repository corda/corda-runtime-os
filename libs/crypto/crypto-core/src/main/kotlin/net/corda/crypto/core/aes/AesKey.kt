package net.corda.crypto.core.aes

import net.corda.crypto.core.Decryptor
import net.corda.crypto.core.Encryptor
import net.corda.crypto.core.ManagedSecret
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * AES key wrapper (with the key length of 256) which supports wrapping/unwrapping
 */
class AesKey(
    internal val key: SecretKey
) {
    companion object {
        /**
         * Creates an instance of [AesKey] by generating a new AES key.
         */
        fun generate(): AesKey {
            val keyGenerator = KeyGenerator.getInstance(AES_KEY_ALGORITHM, AES_PROVIDER)
            keyGenerator.init(AES_KEY_SIZE)
            return AesKey(keyGenerator.generateKey())
        }

        /**
         * Creates an instance of [AesKey] by derives the AES key using passphrase and salt.
         */
        fun derive(passphrase: String, salt: String): AesKey {
            val encoded = encodePassPhrase(passphrase, salt)
            return AesKey(
                key = SecretKeySpec(encoded, AES_KEY_ALGORITHM)
            )
        }

        /**
         * Derives the secret from the passphrase and salt using PBKDF2WithHmacSHA256 algorithm.
         * The return byte array can be used as the AES key or hash for a password.
         */
        fun encodePassPhrase(passphrase: String, salt: String, iterCount: Int = AES_DERIVE_ITERATION_COUNT): ByteArray {
            require(passphrase.isNotBlank()) {
                "The passphrase must not be blank string."
            }
            require(salt.isNotBlank()) {
                "The salt must not be blank string."
            }
            val factory = SecretKeyFactory.getInstance(
                AES_DERIVE_ALGORITHM,
                AES_PROVIDER
            )
            val spec = PBEKeySpec(passphrase.toCharArray(), salt.toByteArray(), iterCount, AES_KEY_SIZE)
            val tmp = factory.generateSecret(spec)
            return tmp.encoded
        }
    }

    private val _encryptor = AesEncryptor(this)

    /**
     * Instance of [Encryptor] which supports encryption using this key (AES algorithm with
     * the key length of 256)
     */
    val encryptor: Encryptor = _encryptor

    /**
     * Instance of [Decryptor] which supports decryption using this key (AES algorithm with
     * the key length of 256)
     */
    val decryptor: Decryptor = _encryptor

    /**
     * Encrypts (or wraps) the '[other]' [AesKey].
     *
     * @return [ByteArray] which represents `[other]` [AesKey].
     *
     * The [AesKey] can be restored from [ByteArray] by using `[unwrapKey]` method.
     */
    fun wrapKey(other: AesKey): ByteArray = encryptor.encrypt(other.key.encoded)

    /**
     * Decrypts (or unwraps) the '[other]' to [AesKey].
     */
    fun unwrapKey(other: ByteArray): AesKey = AesKey(SecretKeySpec(decryptor.decrypt(other), AES_KEY_ALGORITHM))

    /**
     * Encrypts (or wraps) the [ManagedSecret].
     *
     * @return [ByteArray] which represents '[secret]' [ManagedSecret].
     *
     * The [ManagedSecret] can be restored from [ByteArray] by using `[unwrapSecret]` method.
     */
    fun wrapSecret(secret: ManagedSecret): ByteArray = encryptor.encrypt(secret.secret)

    /**
     * Decrypts (or unwraps) the '[secret]' to  [ManagedSecret].
     */
    fun unwrapSecret(secret: ByteArray): ManagedSecret = ManagedSecret(decryptor.decrypt(secret))

    override fun hashCode(): Int {
        return key.hashCode()
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as AesKey
        return key == other.key
    }
}