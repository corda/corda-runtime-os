package net.corda.crypto.cipher.suite

import net.corda.crypto.cipher.suite.schemes.KeyScheme
import java.security.PublicKey

/**
 * Parameters for the Diffie–Hellman key agreement shared secret derivation when using the private key stored in the HSM.
 *
 * @property publicKey The public key of the pair.
 * @property hsmAlias The key pair alias assigned by the implementation when the key was generated.
 * @property keyScheme The scheme for the key used for the operation.
 * @property otherPublicKey The public of the "other" party which should be used to derive the secret.
 */
class SharedSecretAliasSpec(
    val hsmAlias: String,
    override val publicKey: PublicKey,
    override val keyScheme: KeyScheme,
    override val otherPublicKey: PublicKey
) : SharedSecretSpec {
    override fun toString(): String =
        "$keyScheme,hsmAlias=$hsmAlias,otherPublicKey=${PublicKeyHash.calculate(otherPublicKey).id}"
}