package net.corda.v5.cipher.suite

import net.corda.v5.cipher.suite.schemes.KeyScheme
import net.corda.v5.crypto.SignatureSpec
import java.security.PublicKey

/**
 * Parameters for signing operation when using the key stored in the HSM.
 *
 * @property publicKey The public key of the pair.
 * @property hsmAlias The key pair alias assigned by the implementation when the key was generated.
 * @property keyScheme The scheme for the key used for signing operation.
 * @property signatureSpec The signature spec to use for signing, such as SHA256withECDSA, etc.
 */
class SigningAliasSpec(
    val hsmAlias: String,
    override val publicKey: PublicKey,
    override val keyScheme: KeyScheme,
    override val signatureSpec: SignatureSpec
) : SigningSpec {
    override fun toString(): String =
        "$keyScheme,hsmAlias=$hsmAlias,sig=$signatureSpec"
}