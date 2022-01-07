package net.corda.crypto

import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * Supports encryption/decryption operations using AES algorithm with the key length of 256
 */
class Encryptor(
    private val key: SecretKey
) {
    companion object {
        private const val IV_LENGTH = 16
        private const val TRANSFORMATION = "AES/CBC/PKCS5Padding"
        private const val DERIVE_ALGORITHM = "PBKDF2WithHmacSHA256"
        private const val DERIVE_ITERATION_COUNT = 65536
        const val WRAPPING_KEY_ALGORITHM = "AES"
        const val AES_KEY_LENGTH = 256
        const val AES_PROVIDER = "SunJCE"

        private val secureRandom = SecureRandom()

        /**
         * Creates an instance of [Encryptor] by generating a new AES key.
         */
        fun generate(): Encryptor {
            val keyGenerator = KeyGenerator.getInstance(WRAPPING_KEY_ALGORITHM, AES_PROVIDER)
            keyGenerator.init(AES_KEY_LENGTH)
            return Encryptor(keyGenerator.generateKey())
        }

        /**
         * Creates an instance of [Encryptor] by derives the AES key using passphrase and salt.
         */
        fun derive(passphrase: String, salt: String): Encryptor {
            require(passphrase.isNotBlank()) {
                "The passphrase must not be blank string."
            }
            require(salt.isNotBlank()) {
                "The salt must not be blank string."
            }
            val encoded = encodePassPhrase(passphrase, salt)
            return Encryptor(
                key = SecretKeySpec(encoded, WRAPPING_KEY_ALGORITHM)
            )
        }

        /**
         * Derives the secret from the passphrase and salt using PBKDF2WithHmacSHA256 algorithm.
         * The return byte array can be used as the AES key or hash for a password.
         */
        fun encodePassPhrase(passphrase: String, salt: String, iterCount: Int = DERIVE_ITERATION_COUNT): ByteArray {
            /* Derive the key, given password and salt. */
            val factory = SecretKeyFactory.getInstance(
                DERIVE_ALGORITHM,
                AES_PROVIDER
            )
            val spec = PBEKeySpec(passphrase.toCharArray(), salt.toByteArray(), iterCount, AES_KEY_LENGTH)
            val tmp = factory.generateSecret(spec)
            return tmp.encoded
        }

    }

    /**
     * Encrypts (or wraps) the "other" encryptor.
     *
     * @return [ByteArray] which represents `other` [Encryptor].
     * The [Encryptor] can be restored from [ByteArray] by using `unwrap()` method.
     */
    fun wrap(other: Encryptor): ByteArray = encrypt(other.key.encoded)

    /**
     * Decrypts (or unwraps) the "other" encryptor.
     */
    fun unwrap(other: ByteArray): Encryptor = Encryptor(SecretKeySpec(decrypt(other), WRAPPING_KEY_ALGORITHM))

    /**
     * Encrypts the given byte array.
     */
    fun encrypt(raw: ByteArray): ByteArray {
        val ivBytes = ByteArray(IV_LENGTH).apply {
            secureRandom.nextBytes(this)
        }
        return ivBytes + Cipher.getInstance(TRANSFORMATION, AES_PROVIDER).apply {
            init(Cipher.ENCRYPT_MODE, key, IvParameterSpec(ivBytes))
        }.doFinal(raw)
    }

    /**
     * Decrypts the given byte array.
     */
    fun decrypt(raw: ByteArray): ByteArray {
        val ivBytes = raw.sliceArray(0 until IV_LENGTH)
        val keyBytes = raw.sliceArray(IV_LENGTH until raw.size)
        return Cipher.getInstance(TRANSFORMATION, AES_PROVIDER).apply {
            init(Cipher.DECRYPT_MODE, key, IvParameterSpec(ivBytes))
        }.doFinal(keyBytes)
    }

    override fun hashCode(): Int {
        return key.hashCode()
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as Encryptor
        return key == other.key
    }
}