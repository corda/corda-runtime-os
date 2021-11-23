package net.corda.crypto.impl.persistence

import net.corda.crypto.PasswordEncodeUtils.AES_KEY_LENGTH
import net.corda.crypto.PasswordEncodeUtils.encodePassPhrase
import net.corda.v5.base.util.contextLogger
import net.corda.v5.cipher.suite.CipherSchemeMetadata
import org.bouncycastle.asn1.pkcs.PrivateKeyInfo
import java.security.PrivateKey
import java.security.spec.PKCS8EncodedKeySpec
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

class WrappingKey(
    private val master: SecretKey,
    private val schemeMetadata: CipherSchemeMetadata,
) {
    companion object {

        private val logger = contextLogger()

        private const val IV_LENGTH = 16

        private const val AES_PROVIDER = "SunJCE"
        const val WRAPPING_KEY_ALGORITHM = "AES"
        private const val TRANSFORMATION = "AES/CBC/PKCS5Padding"

        fun deriveMasterKey(schemeMetadata: CipherSchemeMetadata, originalPassphrase: String?, originalSalt: String?): WrappingKey {

            val passphrase = if (originalPassphrase.isNullOrBlank()) {
                logger.warn("Please specify the passphrase in the configuration, for now will be using the dev value!")
                "PASSPHRASE"
            } else {
                originalPassphrase
            }
            val salt = if (originalSalt.isNullOrBlank()) {
                logger.warn("Please specify the salt in the configuration, for now will be using the dev value!")
                "SALT"
            } else {
                originalSalt
            }

            val encoded = encodePassPhrase(passphrase, salt)
            return WrappingKey(
                schemeMetadata = schemeMetadata,
                master = SecretKeySpec(encoded, WRAPPING_KEY_ALGORITHM)
            )
        }

        fun createWrappingKey(schemeMetadata: CipherSchemeMetadata): WrappingKey {
            val keyGenerator = KeyGenerator.getInstance(WRAPPING_KEY_ALGORITHM)
            keyGenerator.init(AES_KEY_LENGTH)
            return WrappingKey(
                schemeMetadata = schemeMetadata,
                master = keyGenerator.generateKey()
            )
        }
    }

    fun wrap(key: WrappingKey): ByteArray = encrypt(key.master.encoded)

    fun wrap(key: PrivateKey): ByteArray = encrypt(key.encoded)

    fun unwrapWrappingKey(key: ByteArray): WrappingKey = WrappingKey(
        schemeMetadata = schemeMetadata,
        master = decrypt(key).decodeSecretKey()
    )

    fun unwrap(key: ByteArray): PrivateKey = decrypt(key).decodePrivateKey()

    private fun encrypt(raw: ByteArray): ByteArray {
        val ivBytes = ByteArray(IV_LENGTH).apply {
            schemeMetadata.secureRandom.nextBytes(this)
        }
        return ivBytes + Cipher.getInstance(TRANSFORMATION, AES_PROVIDER).apply {
            init(Cipher.ENCRYPT_MODE, master, IvParameterSpec(ivBytes))
        }.doFinal(raw)
    }

    private fun decrypt(raw: ByteArray): ByteArray {
        val ivBytes = raw.sliceArray(0 until IV_LENGTH)
        val keyBytes = raw.sliceArray(IV_LENGTH until raw.size)
        return Cipher.getInstance(TRANSFORMATION, AES_PROVIDER).apply {
            init(Cipher.DECRYPT_MODE, master, IvParameterSpec(ivBytes))
        }.doFinal(keyBytes)
    }

    private fun ByteArray.decodePrivateKey(): PrivateKey {
        val keyInfo = PrivateKeyInfo.getInstance(this)
        val scheme = schemeMetadata.findSignatureScheme(keyInfo.privateKeyAlgorithm)
        val keyFactory = schemeMetadata.findKeyFactory(scheme)
        return schemeMetadata.toSupportedPrivateKey(keyFactory.generatePrivate(PKCS8EncodedKeySpec(this)))
    }

    private fun ByteArray.decodeSecretKey(): SecretKey =
        SecretKeySpec(this, 0, size, WRAPPING_KEY_ALGORITHM)
}