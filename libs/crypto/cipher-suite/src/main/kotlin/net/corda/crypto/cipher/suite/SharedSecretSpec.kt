package net.corda.crypto.cipher.suite

import net.corda.crypto.cipher.suite.schemes.KeyScheme
import java.security.PublicKey

/**
 * Marker interface denoting the Diffie–Hellman key agreement shared secret derivation.
 *
 * @property publicKey The public key of the pair.
 * @property keyScheme The scheme for the key used for the operation.
 * @property otherPublicKey The public of the "other" party which should be used to derive the secret.
 */
interface SharedSecretSpec {
    val publicKey: PublicKey
    val keyScheme: KeyScheme
    val otherPublicKey: PublicKey
}