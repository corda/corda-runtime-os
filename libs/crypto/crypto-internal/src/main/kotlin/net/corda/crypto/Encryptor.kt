package net.corda.crypto

import net.corda.crypto.PasswordEncodeUtils.AES_PROVIDER
import net.corda.crypto.PasswordEncodeUtils.encodePassPhrase
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

class Encryptor(
    private val key: SecretKey
) {
    companion object {
        private const val IV_LENGTH = 16
        private const val TRANSFORMATION = "AES/CBC/PKCS5Padding"
        const val WRAPPING_KEY_ALGORITHM = "AES"

        private val secureRandom = SecureRandom()

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
    }

    fun encrypt(other: Encryptor): ByteArray = encrypt(other.key.encoded)

    fun encrypt(raw: ByteArray): ByteArray {
        val ivBytes = ByteArray(IV_LENGTH).apply {
            secureRandom.nextBytes(this)
        }
        return ivBytes + Cipher.getInstance(TRANSFORMATION, AES_PROVIDER).apply {
            init(Cipher.ENCRYPT_MODE, key, IvParameterSpec(ivBytes))
        }.doFinal(raw)
    }

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