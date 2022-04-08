package net.corda.crypto.core.aes

import net.corda.crypto.core.ManagedKey
import net.corda.v5.cipher.suite.CipherSchemeMetadata
import org.bouncycastle.asn1.pkcs.PrivateKeyInfo
import java.security.PrivateKey
import java.security.spec.PKCS8EncodedKeySpec

class WrappingKey(
    private val key: ManagedKey,
    private val schemeMetadata: CipherSchemeMetadata,
) {
    companion object {
        fun derive(schemeMetadata: CipherSchemeMetadata, passphrase: String, salt: String): WrappingKey =
            WrappingKey(
                schemeMetadata = schemeMetadata,
                key = AesKey.derive(passphrase, salt)
            )

        fun createWrappingKey(schemeMetadata: CipherSchemeMetadata): WrappingKey =
            WrappingKey(
                schemeMetadata = schemeMetadata,
                key = AesKey.generate()
            )
    }

    val algorithm: String = key.algorithm

    fun wrap(key: WrappingKey): ByteArray = this.key.wrapKey(key.key)

    fun wrap(key: PrivateKey): ByteArray = this.key.encryptor.encrypt(key.encoded)

    fun unwrapWrappingKey(key: ByteArray): WrappingKey = WrappingKey(
        schemeMetadata = schemeMetadata,
        key = this.key.unwrapKey(key)
    )

    fun unwrap(key: ByteArray): PrivateKey = this.key.encryptor.decrypt(key).decodePrivateKey()

    private fun ByteArray.decodePrivateKey(): PrivateKey {
        val keyInfo = PrivateKeyInfo.getInstance(this)
        val scheme = schemeMetadata.findSignatureScheme(keyInfo.privateKeyAlgorithm)
        val keyFactory = schemeMetadata.findKeyFactory(scheme)
        return keyFactory.generatePrivate(PKCS8EncodedKeySpec(this))
    }

    override fun hashCode(): Int {
        return key.hashCode()
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as WrappingKey
        return key == other.key
    }
}