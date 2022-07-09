package net.corda.crypto.ecdh

import java.security.PublicKey

// the KeyScheme for the ephemeral keys must be the same as for otherStablePublicKey
interface EphemeralKeyPairProvider {
    fun create(otherStablePublicKey: PublicKey, digestName: String): ECDHKeyPair
}