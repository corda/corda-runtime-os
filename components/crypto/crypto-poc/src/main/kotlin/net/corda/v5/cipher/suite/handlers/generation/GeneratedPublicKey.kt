package net.corda.v5.cipher.suite.handlers.generation

import java.security.PublicKey

/**
 * Holding class for the public key of the generated key pair when the key is stored in the HSM.
 *
 * @property publicKey The public key of the pair.
 * @property hsmAlias The alias which was used to persist the key pair in the HSM.
 *
 * The Corda Platform always uses single alias to identify a key pair however some HSMs need separate
 * aliases for public and private keys, in such cases their names have to be derived from the single key pair alias.
 * It could be suffixes or whatever internal naming scheme is used.
 */
class GeneratedPublicKey(
    override val publicKey: PublicKey,
    val hsmAlias: String
) : GeneratedKey