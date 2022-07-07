package net.corda.crypto.core.aes.ecdh

import net.corda.crypto.core.aes.AES_PROVIDER
import net.corda.crypto.core.aes.GCM_NONCE_LENGTH
import net.corda.crypto.core.aes.GCM_TAG_LENGTH
import net.corda.crypto.core.aes.GCM_TRANSFORMATION
import java.util.concurrent.ConcurrentLinkedQueue
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

class AesGcmEncryptor(
    private val key: SecretKey
) {
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
     * Encrypts the given byte array using the provided nonce.
     */
    fun encrypt(plainText: ByteArray, nonce: ByteArray): ByteArray {
        require(nonce.size == GCM_NONCE_LENGTH) {
            "The nonce length must be exactly $GCM_NONCE_LENGTH"
        }
        return withGcmCipherInstance {
            init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(GCM_TAG_LENGTH * 8, nonce))
            doFinal(plainText)
        }
    }

    /**
     * Decrypts the given byte array using the provided nonce.
     */
    fun decrypt(cipherText: ByteArray, nonce: ByteArray): ByteArray {
        require(nonce.size == GCM_NONCE_LENGTH) {
            "The nonce length must be exactly $GCM_NONCE_LENGTH"
        }
        return withGcmCipherInstance {
            val spec = GCMParameterSpec(GCM_TAG_LENGTH * 8, nonce)
            init(Cipher.DECRYPT_MODE, key, spec)
            doFinal(cipherText)
        }
    }
}