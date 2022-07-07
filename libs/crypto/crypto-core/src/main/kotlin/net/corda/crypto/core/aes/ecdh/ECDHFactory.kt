package net.corda.crypto.core.aes.ecdh

import net.corda.crypto.core.aes.ecdh.protocol.Initiator
import net.corda.crypto.core.aes.ecdh.protocol.Replier
import net.corda.v5.cipher.suite.schemes.KeyScheme
import net.corda.v5.crypto.DigestAlgorithmName
import net.corda.v5.crypto.SignatureSpec
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

    fun createAgreementParams(
        digestName: String = DigestAlgorithmName.SHA2_256.name,
        length: Int = 32
    ): ECDHAgreementParams
}