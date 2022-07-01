package net.corda.crypto.core.aes

import net.corda.crypto.core.Encryptor
import net.corda.crypto.core.concatByteArrays
import java.util.concurrent.ConcurrentLinkedQueue
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec

/**
 * Supports encryption/decryption operations using AES algorithm with the key length of 256
 */
class AesEncryptor(
    private val key: AesKey
) : Encryptor {
    companion object {
        private val pool = ConcurrentLinkedQueue<Cipher>()

        private fun withGcmCipherInstance(block: Cipher.() -> ByteArray): ByteArray {
            val cipher = pool.poll()
                ?: Cipher.getInstance(GCM_TRANSFORMATION, AES_PROVIDER)
            try {
                return cipher.block()
            } finally {
                pool.offer(cipher)
            }
        }

    }

    /**
     * Encrypts the given byte array.
     */
    override fun encrypt(plainText: ByteArray): ByteArray {
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
    override fun decrypt(cipherText: ByteArray): ByteArray {
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
        other as AesEncryptor
        return key == other.key
    }
}