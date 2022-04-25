package net.corda.v5.cipher.suite

import net.corda.v5.cipher.suite.schemes.SignatureScheme

/**
 * Holding class for the key pair which is persisted in HSM and referenced by its alias.
 *
 * @property tenantId The tenant id which the key pair belongs to.
 * @property hsmAlias The key pair alias assigned by the implementation when the key was generated.
 * @property signatureScheme The scheme for the signing operation.
 *
 * Note about key aliases. Corda always uses single alias to identify a key pair however some HSMs need separate
 * aliases for public and private keys, in such cases their names have to be derived from the single key pair alias.
 * It could be suffixes or whatever internal naming scheme is used.
 */
class SigningAliasSpec(
    override val tenantId: String,
    val hsmAlias: String,
    override val signatureScheme: SignatureScheme
) : SigningSpec