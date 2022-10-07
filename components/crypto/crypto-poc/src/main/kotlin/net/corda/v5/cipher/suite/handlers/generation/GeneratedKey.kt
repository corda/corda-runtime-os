package net.corda.v5.cipher.suite.handlers.generation

import java.security.PublicKey

/**
 * Marker interface denoting the generated key pair.
 *
 * @property publicKey The public key of the pair.
 */
interface GeneratedKey {
    val publicKey: PublicKey
}