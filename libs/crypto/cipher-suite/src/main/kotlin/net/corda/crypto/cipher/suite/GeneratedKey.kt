package net.corda.crypto.cipher.suite

import java.security.PublicKey

/**
 * Marker interface denoting the generated key pair by the [CryptoService].
 *
 * @property publicKey The public key of the pair.
 */
interface GeneratedKey {
    val publicKey: PublicKey
}