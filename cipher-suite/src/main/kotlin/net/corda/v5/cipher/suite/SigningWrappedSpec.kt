package net.corda.v5.cipher.suite

import net.corda.v5.cipher.suite.schemes.KeyScheme
import net.corda.v5.crypto.SignatureSpec

/**
 * Parameters for signing operation when using the wrapped key.
 *
 * @property keyMaterialSpec The spec for the wrapped key.
 * @property keyScheme The scheme for the key used for signing operation.
 * @property signatureSpec The signature spec to use for signing, such as SHA256withECDSA, etc.
 */
class SigningWrappedSpec(
    val keyMaterialSpec: KeyMaterialSpec,
    override val keyScheme: KeyScheme,
    override val signatureSpec: SignatureSpec
) : SigningSpec {
    override fun toString(): String =
        "$keyScheme,sig=$signatureSpec,spec=$keyMaterialSpec"
}