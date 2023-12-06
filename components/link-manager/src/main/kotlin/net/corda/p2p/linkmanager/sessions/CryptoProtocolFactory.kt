package net.corda.p2p.linkmanager.sessions

import net.corda.data.p2p.crypto.ProtocolMode
import net.corda.p2p.crypto.protocol.api.AuthenticationProtocolInitiator
import net.corda.p2p.crypto.protocol.api.AuthenticationProtocolResponder
import net.corda.p2p.crypto.protocol.api.CertificateCheckMode
import java.security.PublicKey

internal class CryptoProtocolFactory: ProtocolFactory {
    override fun createInitiator(
        sessionId: String,
        supportedModes: Set<ProtocolMode>,
        ourMaxMessageSize: Int,
        ourPublicKey: PublicKey,
        groupId: String,
        mode: CertificateCheckMode,
        revocationCheckerClient: RevocationCheckerClient,
    ): AuthenticationProtocolInitiator {
        return AuthenticationProtocolInitiator.create(
            sessionId,
            supportedModes,
            ourMaxMessageSize,
            ourPublicKey,
            groupId,
            mode,
            revocationCheckerClient,
        )
    }

    override fun createResponder(sessionId: String, ourMaxMessageSize: Int): AuthenticationProtocolResponder {
        return AuthenticationProtocolResponder.create(
            sessionId,
            ourMaxMessageSize,
        )
    }
}