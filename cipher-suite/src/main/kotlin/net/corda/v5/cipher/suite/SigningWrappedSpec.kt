package net.corda.v5.cipher.suite

import net.corda.v5.cipher.suite.schemes.KeyScheme
import net.corda.v5.crypto.SignatureSpec

/**
 * Parameters for signing operation when using the wrapped key.
 *
 * @property keyMaterial The encoded and encrypted private key.
 * @property masterKeyAlias The wrapping key's alias which was used for wrapping, the value
 * could still be null for HSMs which use built-in wrapping keys.
 * @property encodingVersion The encoding version which was used to encode the private key.
 * @property keyScheme The scheme for the key used for signing operation.
 * @property signatureSpec The signature spec to use for signing, such as SHA256withECDSA, etc.
 */
class SigningWrappedSpec(
    val keyMaterial: ByteArray,
    val masterKeyAlias: String?,
    val encodingVersion: Int,
    override val keyScheme: KeyScheme,
    override val signatureSpec: SignatureSpec
) : SigningSpec {
    override fun toString(): String =
        "$keyScheme,masterKeyAlias=$masterKeyAlias,encVer=$encodingVersion,sig=$signatureSpec"
}