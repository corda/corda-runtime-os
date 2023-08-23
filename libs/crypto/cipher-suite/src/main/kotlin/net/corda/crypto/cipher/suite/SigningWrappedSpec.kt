package net.corda.crypto.cipher.suite

import net.corda.crypto.cipher.suite.schemes.KeyScheme
import net.corda.v5.crypto.SignatureSpec
import java.security.PublicKey

/**
 * Parameters for signing operation when using the wrapped key.
 *
 * @param publicKey The public key of the pair.
 * @param keyMaterialSpec The spec for the wrapped key.
 * @param keyScheme The scheme for the key used for signing operation.
 * @param signatureSpec The signature spec to use for signing, such as SHA256withECDSA, etc.
 * @param category The category of the key, if specified.
 */
data class SigningWrappedSpec(
    /**
     * The spec for the wrapped key.
     */
    val keyMaterialSpec: KeyMaterialSpec,
    val publicKey: PublicKey,
    val keyScheme: KeyScheme,
    val signatureSpec: SignatureSpec,
    val category: String? = null
) {

    /**
     * Converts a [SigningWrappedSpec] object to a string representation.
     */
    override fun toString(): String =
        "$keyScheme,sig=$signatureSpec,spec=$keyMaterialSpec"
}