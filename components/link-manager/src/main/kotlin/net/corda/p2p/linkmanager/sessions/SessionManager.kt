package net.corda.p2p.linkmanager.sessions

import net.corda.p2p.AuthenticatedMessageAndKey
import net.corda.p2p.LinkInMessage
import net.corda.p2p.LinkOutMessage
import net.corda.p2p.crypto.protocol.api.Session

interface SessionManager {
    fun processOutboundFlowMessage(message: AuthenticatedMessageAndKey): SessionState
    fun getSessionById(uuid: String): SessionDirection
    fun processSessionMessage(message: LinkInMessage): LinkOutMessage?

    sealed class SessionState {
        data class NewSessionNeeded(val sessionId: String, val sessionInitMessage: LinkOutMessage): SessionState()
        object SessionAlreadyPending: SessionState()
        data class SessionEstablished(val session: Session): SessionState()
        object CannotEstablishSession: SessionState()
    }

    sealed class SessionDirection {
        data class Inbound(val session: Session): SessionDirection()
        data class Outbound(val session: Session): SessionDirection()
        object NoSession: SessionDirection()
    }
}