package net.corda.crypto.cipher.suite

import net.corda.crypto.cipher.suite.schemes.KeyScheme
import net.corda.v5.crypto.SignatureSpec
import java.security.PublicKey

/**
 * Parameters for signing operation when using the key stored in the HSM.
 *
 * @param publicKey The public key of the pair.
 * @param hsmAlias The key pair alias assigned by the implementation when the key was generated.
 * @param keyScheme The scheme for the key used for signing operation.
 * @param signatureSpec The signature spec to use for signing, such as SHA256withECDSA, etc.
 */
class SigningAliasSpec(
    /**
     * The key pair alias assigned by the implementation when the key was generated.
     */
    val hsmAlias: String,
    override val publicKey: PublicKey,
    override val keyScheme: KeyScheme,
    override val signatureSpec: SignatureSpec
) : SigningSpec {

    /**
     * Converts a [SigningAliasSpec] object to a string representation.
     */
    override fun toString(): String =
        "$keyScheme,hsmAlias=$hsmAlias,sig=$signatureSpec"
}