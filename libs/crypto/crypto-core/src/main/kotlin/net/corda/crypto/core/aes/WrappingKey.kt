package net.corda.crypto.core.aes

import net.corda.crypto.cipher.suite.CipherSchemeMetadata
import net.corda.crypto.core.ManagedKey
import org.bouncycastle.asn1.pkcs.PrivateKeyInfo
import java.security.PrivateKey
import java.security.spec.PKCS8EncodedKeySpec
import javax.crypto.Cipher

/**
 * Wrapping key which can wrap/unwrap other [WrappingKey]s or [PrivateKey]s.
 */
interface WrappingKey {

    val key: ManagedKey

    val schemeMetadata: CipherSchemeMetadata

    /**
     * Returns the standard algorithm name for this key.
     * See the Java Security Standard Algorithm Names document for more information.
     */
    val algorithm: String

    /**
     * Encrypts the [other] [WrappingKey].
     */
    fun wrap(other: WrappingKey): ByteArray

    /**
     * Encrypts the [other] [PrivateKey].
     */
    fun wrap(other: PrivateKey): ByteArray

    /**
     * Decrypts the [other] [WrappingKey].
     */
    fun unwrapWrappingKey(other: ByteArray): WrappingKey

    /**
     * Decrypts the [other] [PrivateKey].
     */
    fun unwrap(other: ByteArray): PrivateKey
}