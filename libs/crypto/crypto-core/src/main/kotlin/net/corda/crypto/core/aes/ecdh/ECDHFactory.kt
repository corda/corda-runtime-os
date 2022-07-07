package net.corda.crypto.core.aes.ecdh

import net.corda.crypto.core.aes.ecdh.protocol.Initiator
import net.corda.crypto.core.aes.ecdh.protocol.Replier
import net.corda.v5.cipher.suite.schemes.KeyScheme
import net.corda.v5.crypto.DigestAlgorithmName
import net.corda.v5.crypto.SignatureSpec
import java.security.PublicKey

interface ECDHFactory {
    fun createInitiator(
        otherStablePublicKey: PublicKey,
        ephemeralScheme: KeyScheme,
        signatureSpec: SignatureSpec
    ): Initiator

    fun createReplier(
        stablePublicKey: PublicKey,
        ephemeralScheme: KeyScheme,
        signatureSpec: SignatureSpec
    ): Replier

    fun createAgreementParams(
        digestName: String = DigestAlgorithmName.SHA2_256.name,
        length: Int = 32
    ): ECDHAgreementParams
}