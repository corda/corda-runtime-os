package net.corda.p2p.linkmanager.sessions

import net.corda.p2p.LinkInMessage
import net.corda.p2p.LinkOutMessage
import net.corda.p2p.crypto.protocol.api.Session
import net.corda.p2p.payload.FlowMessageAndKey

interface SessionManager {
    fun processOutboundFlowMessage(message: FlowMessageAndKey): SessionState
    fun getInboundSession(uuid: String): Session?
    fun processSessionMessage(message: LinkInMessage): LinkOutMessage?

    sealed class SessionState {
        data class NewSessionNeeded(val sessionId: String, val sessionInitMessage: LinkOutMessage): SessionState()
        object SessionAlreadyPending: SessionState()
        data class SessionEstablished(val session: Session): SessionState()
        object CannotEstablishSession: SessionState()
    }
}