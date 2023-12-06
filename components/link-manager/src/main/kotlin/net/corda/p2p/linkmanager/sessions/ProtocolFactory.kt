package net.corda.p2p.linkmanager.sessions

import net.corda.data.p2p.crypto.ProtocolMode
import net.corda.p2p.crypto.protocol.api.AuthenticationProtocolInitiator
import net.corda.p2p.crypto.protocol.api.AuthenticationProtocolResponder
import net.corda.p2p.crypto.protocol.api.CertificateCheckMode
import java.security.PublicKey

internal interface ProtocolFactory {
    @Suppress("LongParameterList")
    fun createInitiator(
        sessionId: String,
        supportedModes: Set<ProtocolMode>,
        ourMaxMessageSize: Int,
        ourPublicKey: PublicKey,
        groupId: String,
        mode: CertificateCheckMode,
        revocationCheckerClient: RevocationCheckerClient,
    ): AuthenticationProtocolInitiator
    fun createResponder(sessionId: String, ourMaxMessageSize: Int): AuthenticationProtocolResponder
}