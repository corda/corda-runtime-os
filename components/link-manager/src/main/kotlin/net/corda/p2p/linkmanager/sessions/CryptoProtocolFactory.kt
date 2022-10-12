package net.corda.p2p.linkmanager.sessions

import net.corda.p2p.crypto.ProtocolMode
import net.corda.p2p.crypto.protocol.api.AuthenticationProtocolInitiator
import net.corda.p2p.crypto.protocol.api.AuthenticationProtocolResponder
import net.corda.p2p.crypto.protocol.api.PkiMode
import java.security.PublicKey

internal class CryptoProtocolFactory: ProtocolFactory {
    override fun createInitiator(sessionId: String, supportedModes: Set<ProtocolMode>, ourMaxMessageSize: Int,
                                 ourPublicKey: PublicKey, groupId: String, mode: PkiMode): AuthenticationProtocolInitiator {
        return AuthenticationProtocolInitiator(sessionId, supportedModes, ourMaxMessageSize, ourPublicKey, groupId, mode)
    }

    override fun createResponder(sessionId: String, supportedModes: Set<ProtocolMode>, ourMaxMessageSize: Int,
                                 mode: PkiMode): AuthenticationProtocolResponder {
        return AuthenticationProtocolResponder(sessionId, supportedModes, ourMaxMessageSize, mode)
    }
}