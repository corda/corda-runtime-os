package net.corda.crypto.core.aes.ecdh

import net.corda.crypto.core.aes.ecdh.protocol.Initiator
import net.corda.crypto.core.aes.ecdh.protocol.Replier
import java.security.PublicKey

// the KeyScheme for the ephemeral keys must be the same as for otherStablePublicKey

interface ECDHFactory {
    companion object {
        val HKDF_INITIAL_KEY_INFO: ByteArray = "corda-mgm-initial-handshake".toByteArray()
    }

    fun createInitiator(
        otherStablePublicKey: PublicKey
    ): Initiator

    fun createReplier(
        stablePublicKey: PublicKey
    ): Replier
}