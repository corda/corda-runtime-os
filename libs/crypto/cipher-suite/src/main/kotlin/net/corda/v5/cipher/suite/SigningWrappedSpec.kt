package net.corda.v5.cipher.suite

import net.corda.v5.cipher.suite.schemes.KeyScheme
import net.corda.v5.crypto.SignatureSpec
import java.security.PublicKey

/**
 * Parameters for signing operation when using the wrapped key.
 *
 * @param publicKey The public key of the pair.
 * @param keyMaterialSpec The spec for the wrapped key.
 * @param keyScheme The scheme for the key used for signing operation.
 * @param signatureSpec The signature spec to use for signing, such as SHA256withECDSA, etc.
 */
class SigningWrappedSpec(
    /**
     * The spec for the wrapped key.
     */
    val keyMaterialSpec: KeyMaterialSpec,
    override val publicKey: PublicKey,
    override val keyScheme: KeyScheme,
    override val signatureSpec: SignatureSpec
) : SigningSpec {

    /**
     * Converts a [SigningWrappedSpec] object to a string representation.
     */
    override fun toString(): String =
        "$keyScheme,sig=$signatureSpec,spec=$keyMaterialSpec"
}