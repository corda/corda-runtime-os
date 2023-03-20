package net.corda.crypto.cipher.suite

import net.corda.crypto.cipher.suite.schemes.KeyScheme
import java.security.PublicKey

/**
 * Parameters for the Diffie–Hellman key agreement shared secret derivation when using the wrapped key.
 *
 * @property publicKey The public key of the pair.
 * @property keyMaterialSpec The spec for the wrapped key.
 * @property keyScheme The scheme for the key used for signing operation.
 * @property otherPublicKey The public of the "other" party which should be used to derive the secret.
 */
class SharedSecretWrappedSpec(
    val keyMaterialSpec: KeyMaterialSpec,
    override val publicKey: PublicKey,
    override val keyScheme: KeyScheme,
    override val otherPublicKey: PublicKey
) : SharedSecretSpec {
    override fun toString(): String =
        "$keyScheme,otherPublicKey=${otherPublicKey.publicKeyId()},spec=$keyMaterialSpec"
}