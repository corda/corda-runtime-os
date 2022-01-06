package net.corda.crypto.impl.persistence

import net.corda.crypto.Encryptor
import net.corda.crypto.Encryptor.Companion.AES_KEY_LENGTH
import net.corda.crypto.Encryptor.Companion.AES_PROVIDER
import net.corda.v5.base.util.contextLogger
import net.corda.v5.cipher.suite.CipherSchemeMetadata
import org.bouncycastle.asn1.pkcs.PrivateKeyInfo
import java.security.PrivateKey
import java.security.spec.PKCS8EncodedKeySpec
import javax.crypto.KeyGenerator

class WrappingKey(
    private val encryptor: Encryptor,
    private val schemeMetadata: CipherSchemeMetadata,
) {
    companion object {
        private val logger = contextLogger()
        const val WRAPPING_KEY_ALGORITHM = Encryptor.WRAPPING_KEY_ALGORITHM

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
            return WrappingKey(
                schemeMetadata = schemeMetadata,
                encryptor = Encryptor.derive(passphrase, salt)
            )
        }

        fun createWrappingKey(schemeMetadata: CipherSchemeMetadata): WrappingKey {
            val keyGenerator = KeyGenerator.getInstance(WRAPPING_KEY_ALGORITHM, AES_PROVIDER)
            keyGenerator.init(AES_KEY_LENGTH)
            return WrappingKey(
                schemeMetadata = schemeMetadata,
                encryptor = Encryptor(keyGenerator.generateKey())
            )
        }
    }

    fun wrap(key: WrappingKey): ByteArray = encryptor.wrap(key.encryptor)

    fun wrap(key: PrivateKey): ByteArray = encryptor.encrypt(key.encoded)

    fun unwrapWrappingKey(key: ByteArray): WrappingKey = WrappingKey(
        schemeMetadata = schemeMetadata,
        encryptor = encryptor.unwrap(key)
    )

    fun unwrap(key: ByteArray): PrivateKey = encryptor.decrypt(key).decodePrivateKey()

    private fun ByteArray.decodePrivateKey(): PrivateKey {
        val keyInfo = PrivateKeyInfo.getInstance(this)
        val scheme = schemeMetadata.findSignatureScheme(keyInfo.privateKeyAlgorithm)
        val keyFactory = schemeMetadata.findKeyFactory(scheme)
        return schemeMetadata.toSupportedPrivateKey(keyFactory.generatePrivate(PKCS8EncodedKeySpec(this)))
    }
}