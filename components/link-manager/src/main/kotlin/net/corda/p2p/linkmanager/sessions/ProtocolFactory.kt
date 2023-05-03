package net.corda.p2p.linkmanager.sessions

import net.corda.p2p.crypto.protocol.api.AuthenticationProtocolInitiator
import net.corda.p2p.crypto.protocol.api.AuthenticationProtocolResponder
import java.security.PublicKey

internal interface ProtocolFactory {
    @Suppress("LongParameterList")
    fun createInitiator(sessionId: String, ourPublicKey: PublicKey, groupId: String): AuthenticationProtocolInitiator
    fun createResponder(sessionId: String): AuthenticationProtocolResponder
}