package net.corda.v5.cipher.suite

import net.corda.v5.cipher.suite.schemes.KeyScheme
import java.security.PublicKey

/**
 * Marker interface denoting the Diffie–Hellman key agreement shared secret derivation.
 *
 * @property keyScheme The scheme for the key used for the operation.
 * @property otherPublicKey the public of the "other" party which should be used to derive the secret.
 */
interface SharedSecretSpec {
    val keyScheme: KeyScheme
    val otherPublicKey: PublicKey
}