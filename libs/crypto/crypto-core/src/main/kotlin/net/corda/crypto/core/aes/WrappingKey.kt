package net.corda.crypto.core.aes

import net.corda.crypto.core.ManagedKey
import net.corda.crypto.core.service.PlatformCipherSuiteMetadata
import org.bouncycastle.asn1.pkcs.PrivateKeyInfo
import java.security.PrivateKey
import java.security.spec.PKCS8EncodedKeySpec

/**
 * Wrapping key which can wrap/unwrap other [WrappingKey]s or [PrivateKey]s.
 */
class WrappingKey(
    private val key: ManagedKey,
    private val metadata: PlatformCipherSuiteMetadata,
) {
    companion object {
        /**
         * Derives a new instance of [WrappingKey] using passphrase and salt delegating that to [AesKey].[derive].
         * The resulting key is deterministic.
         *
         * [metadata] is used to correctly decode a [PrivateKey] when unwrapping it
         */
        fun derive(metadata: PlatformCipherSuiteMetadata, credentials: KeyCredentials): WrappingKey =
            derive(metadata, credentials.passphrase, credentials.salt)

        /**
         * Derives a new instance of [WrappingKey] using passphrase and salt delegating that to [AesKey].[derive].
         * The resulting key is deterministic.
         *
         * [metadata] is used to correctly decode a [PrivateKey] when unwrapping it
         */
        fun derive(metadata: PlatformCipherSuiteMetadata, passphrase: String, salt: String): WrappingKey =
            WrappingKey(
                metadata = metadata,
                key = AesKey.derive(passphrase, salt)
            )

        /**
         * Generates a new instance of [WrappingKey].
         * The resulting key is random.
         */
        fun generateWrappingKey(metadata: PlatformCipherSuiteMetadata): WrappingKey =
            WrappingKey(
                metadata = metadata,
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
        metadata = metadata,
        key = this.key.unwrapKey(other)
    )

    /**
     * Decrypts the [other] [PrivateKey].
     */
    fun unwrap(other: ByteArray): PrivateKey = this.key.encryptor.decrypt(other).decodePrivateKey()

    private fun ByteArray.decodePrivateKey(): PrivateKey {
        val keyInfo = PrivateKeyInfo.getInstance(this)
        val keyFactory = metadata.findKeyFactory(keyInfo.privateKeyAlgorithm)
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