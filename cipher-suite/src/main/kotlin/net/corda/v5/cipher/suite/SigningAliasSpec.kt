package net.corda.v5.cipher.suite

import net.corda.v5.cipher.suite.schemes.KeyScheme
import net.corda.v5.crypto.SignatureSpec

/**
 * Parameters for signing operation when using the key stored in the HSM.
 *
 * @property hsmAlias The key pair alias assigned by the implementation when the key was generated.
 * @property keyScheme The scheme for the key used for signing operation.
 * @property signatureSpec The signature spec to use for signing, such as SHA256withECDSA, etc.
 *
 * Note about key aliases. Corda always uses single alias to identify a key pair however some HSMs need separate
 * aliases for public and private keys, in such cases their names have to be derived from the single key pair alias.
 * It could be suffixes or whatever internal naming scheme is used.
 */
class SigningAliasSpec(
    val hsmAlias: String,
    override val keyScheme: KeyScheme,
    override val signatureSpec: SignatureSpec
) : SigningSpec {
    override fun toString(): String =
        "$keyScheme,hsmAlias=$hsmAlias,sig=$signatureSpec"
}