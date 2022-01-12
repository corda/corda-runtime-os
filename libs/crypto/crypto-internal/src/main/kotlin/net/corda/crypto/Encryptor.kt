package net.corda.crypto

import java.security.SecureRandom
import java.util.concurrent.ConcurrentLinkedQueue
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * Supports encryption/decryption operations using AES algorithm with the key length of 256
 */
class Encryptor(
    private val key: SecretKey
) {
    companion object {
        private const val GCM_NONCE_LENGTH = 12 // in bytes
        private const val GCM_TAG_LENGTH = 16 // in bytes
        private const val GSM_TRANSFORMATION = "AES/GCM/NoPadding"
        private const val GCM_KEY_SIZE = 256
        private const val GCM_PROVIDER = "SunJCE"
        private const val DERIVE_ALGORITHM = "PBKDF2WithHmacSHA256"
        private const val DERIVE_ITERATION_COUNT = 65536
        const val WRAPPING_KEY_ALGORITHM = "AES"

        private val secureRandom = SecureRandom()

        private val pool = ConcurrentLinkedQueue<Cipher>()

        /**
         * Creates an instance of [Encryptor] by generating a new AES key.
         */
        fun generate(): Encryptor {
            val keyGenerator = KeyGenerator.getInstance(WRAPPING_KEY_ALGORITHM, GCM_PROVIDER)
            keyGenerator.init(GCM_KEY_SIZE)
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
                GCM_PROVIDER
            )
            val spec = PBEKeySpec(passphrase.toCharArray(), salt.toByteArray(), iterCount, GCM_KEY_SIZE)
            val tmp = factory.generateSecret(spec)
            return tmp.encoded
        }

        private fun withGcmCipherInstance(block: Cipher.() -> ByteArray): ByteArray {
            val cipher = pool.poll()
                ?: Cipher.getInstance(GSM_TRANSFORMATION, GCM_PROVIDER)
            try {
                return cipher.block()
            } finally {
                pool.offer(cipher)
            }
        }

        private fun concatByteArrays(vararg concat: ByteArray): ByteArray {
            if (concat.isEmpty()) {
                return ByteArray(0)
            }
            val length = concat.sumOf { it.size }
            val output = ByteArray(length)
            var offset = 0
            for (segment in concat) {
                val segmentSize = segment.size
                System.arraycopy(segment, 0, output, offset, segmentSize)
                offset += segmentSize
            }
            return output
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
    fun encrypt(plainText: ByteArray): ByteArray {
        val nonce = ByteArray(GCM_NONCE_LENGTH).apply {
            secureRandom.nextBytes(this)
        }
        return withGcmCipherInstance {
            init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(GCM_TAG_LENGTH * 8, nonce))
            concatByteArrays(nonce, doFinal(plainText))
        }
    }

    /**
     * Decrypts the given byte array.
     */
    fun decrypt(cipherText: ByteArray): ByteArray {
        val nonce = cipherText.sliceArray(0 until GCM_NONCE_LENGTH)
        val cipherTextAndTag = cipherText.sliceArray(GCM_NONCE_LENGTH until cipherText.size)
        return withGcmCipherInstance {
            val spec = GCMParameterSpec(GCM_TAG_LENGTH * 8, nonce)
            init(Cipher.DECRYPT_MODE, key, spec)
            doFinal(cipherTextAndTag)
        }
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