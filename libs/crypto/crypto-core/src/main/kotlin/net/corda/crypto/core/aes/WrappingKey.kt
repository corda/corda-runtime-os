package net.corda.crypto.core.aes

import net.corda.crypto.cipher.suite.CipherSchemeMetadata
import net.corda.crypto.core.ManagedKey
import org.bouncycastle.asn1.pkcs.PrivateKeyInfo
import java.security.PrivateKey
import java.security.spec.PKCS8EncodedKeySpec

/**
 * Wrapping key which can wrap/unwrap other [WrappingKey]s or [PrivateKey]s.
 */
class WrappingKey(
    private val key: ManagedKey,
    private val schemeMetadata: CipherSchemeMetadata,
) {
    companion object {
        /**
         * Derives a new instance of [WrappingKey] using passphrase and salt delegating that to [AesKey].[derive].
         * The resulting key is deterministic.
         *
         * [schemeMetadata] is used to correctly decode a [PrivateKey] when unwrapping it
         */
        fun derive(schemeMetadata: CipherSchemeMetadata, credentials: KeyCredentials): WrappingKey =
            derive(schemeMetadata, credentials.passphrase, credentials.salt)

        /**
         * Derives a new instance of [WrappingKey] using passphrase and salt delegating that to [AesKey].[derive].
         * The resulting key is deterministic.
         *
         * [schemeMetadata] is used to correctly decode a [PrivateKey] when unwrapping it
         */
        fun derive(schemeMetadata: CipherSchemeMetadata, passphrase: String, salt: String): WrappingKey =
            WrappingKey(
                schemeMetadata = schemeMetadata,
                key = AesKey.derive(passphrase, salt)
            )

        /**
         * Generates a new instance of [WrappingKey].
         * The resulting key is random.
         */
        fun generateWrappingKey(schemeMetadata: CipherSchemeMetadata): WrappingKey =
            WrappingKey(
                schemeMetadata = schemeMetadata,
                key = AesKey.generate()
            )
    }

    /**
     * Returns the standard algorithm name for this key.
     * See the Java Security Standard Algorithm Names document for more information.
     */
    val algorithm: String = key.algorithm

    /**
     * Encrypts the [other] [WrappingKey].
     */
    fun wrap(other: WrappingKey): ByteArray = this.key.wrapKey(other.key)

    /**
     * Encrypts the [other] [PrivateKey].
     */
    fun wrap(other: PrivateKey): ByteArray = this.key.encryptor.encrypt(other.encoded)

    /**
     * Decrypts the [other] [WrappingKey].
     */
    fun unwrapWrappingKey(other: ByteArray): WrappingKey = WrappingKey(
        schemeMetadata = schemeMetadata,
        key = this.key.unwrapKey(other)
    )

    /**
     * Decrypts the [other] [PrivateKey].
     */
    fun unwrap(other: ByteArray): PrivateKey = this.key.encryptor.decrypt(other).decodePrivateKey()

    private fun ByteArray.decodePrivateKey(): PrivateKey {
        val keyInfo = PrivateKeyInfo.getInstance(this)
        val scheme = schemeMetadata.findKeyScheme(keyInfo.privateKeyAlgorithm)
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