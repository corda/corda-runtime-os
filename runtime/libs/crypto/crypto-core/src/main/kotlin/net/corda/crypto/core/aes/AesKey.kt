package net.corda.crypto.core.aes

import net.corda.crypto.core.Encryptor
import net.corda.crypto.core.ManagedKey
import net.corda.crypto.core.ManagedSecret
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec
import java.util.Base64

/**
 * AES key wrapper (with the key length of 256) which supports wrapping/unwrapping
 */
class AesKey(
    private val key: SecretKey
) : ManagedKey, SecretKey by key {
    companion object {
        const val DERIVE_ALGORITHM = "PBKDF2WithHmacSHA256"

        /**
         * Creates an instance of [AesKey] by generating a new AES key.
         * The resulting key is random.
         */
        fun generate(): AesKey {
            val keyGenerator = KeyGenerator.getInstance(AES_KEY_ALGORITHM, AES_PROVIDER)
            keyGenerator.init(AES_KEY_SIZE)
            return AesKey(keyGenerator.generateKey())
        }

        /**
         * Creates an instance of [AesKey] by derives the AES key using passphrase and salt.
         * The resulting key is deterministic.
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
                DERIVE_ALGORITHM,
                AES_PROVIDER
            )
            val spec = PBEKeySpec(passphrase.toCharArray(), salt.toByteArray(), iterCount, AES_KEY_SIZE)
            val tmp = factory.generateSecret(spec)
            return tmp.encoded
        }
    }

    override val encryptor: Encryptor = AesEncryptor(this)

    override fun wrapKey(other: ManagedKey): ByteArray =
        encryptor.encrypt(other.encoded)

    override fun unwrapKey(other: ByteArray): ManagedKey =
        AesKey(SecretKeySpec(encryptor.decrypt(other), AES_KEY_ALGORITHM))

    override fun wrapSecret(secret: ManagedSecret): ByteArray = encryptor.encrypt(secret.secret)

    override fun unwrapSecret(secret: ByteArray): ManagedSecret = ManagedSecret(encryptor.decrypt(secret))

    override fun hashCode(): Int {
        return key.hashCode()
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as AesKey
        return key == other.key
    }

    // Generate a stable and readable version of a key. To be used with care; for instance
    // logging this reveals the entire key. However, it can be important in debugging to get the bits of the key
    // directly for comaprison purposes.

    override fun toString() = String(Base64.getEncoder().encode(key.encoded))
}