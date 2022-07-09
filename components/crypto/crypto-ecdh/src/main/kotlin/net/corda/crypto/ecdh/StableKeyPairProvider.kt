package net.corda.crypto.ecdh

import net.corda.lifecycle.Lifecycle
import java.security.PublicKey

interface StableKeyPairProvider : Lifecycle {
    fun create(
        tenantId: String,
        publicKey: PublicKey,
        otherPublicKey: PublicKey,
        digestName: String
    ): ECDHKeyPair
}