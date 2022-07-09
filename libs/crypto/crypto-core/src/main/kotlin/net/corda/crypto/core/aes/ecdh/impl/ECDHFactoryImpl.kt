package net.corda.crypto.core.aes.ecdh.impl

import net.corda.crypto.core.aes.ecdh.ECDHFactory
import net.corda.crypto.core.aes.ecdh.impl.protocol.InitiatorImpl
import net.corda.crypto.core.aes.ecdh.impl.protocol.ReplierImpl
import net.corda.crypto.core.aes.ecdh.protocol.Initiator
import net.corda.crypto.core.aes.ecdh.protocol.Replier
import net.corda.v5.cipher.suite.CipherSchemeMetadata
import java.security.PublicKey

//@Component
class ECDHFactoryImpl(
    private val schemeMetadata: CipherSchemeMetadata
) : ECDHFactory {

    override fun createInitiator(
        otherStablePublicKey: PublicKey
    ): Initiator =
        InitiatorImpl(schemeMetadata, otherStablePublicKey)

    override fun createReplier(
        stablePublicKey: PublicKey
    ): Replier =
        ReplierImpl(schemeMetadata, stablePublicKey)
}