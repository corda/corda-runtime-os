package net.corda.p2p.linkmanager.sessions

import net.corda.p2p.crypto.protocol.api.AuthenticationProtocolInitiator
import net.corda.p2p.crypto.protocol.api.AuthenticationProtocolResponder
import java.security.PublicKey

internal class CryptoProtocolFactory: ProtocolFactory {
    override fun createInitiator(sessionId: String, ourPublicKey: PublicKey, groupId: String): AuthenticationProtocolInitiator {
        return AuthenticationProtocolInitiator(sessionId, ourPublicKey, groupId)
    }

    override fun createResponder(sessionId: String): AuthenticationProtocolResponder {
        return AuthenticationProtocolResponder(sessionId)
    }
}