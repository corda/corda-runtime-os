package net.corda.p2p.linkmanager.sessions

import net.corda.lifecycle.domino.logic.LifecycleWithDominoTile
import net.corda.p2p.AuthenticatedMessageAndKey
import net.corda.p2p.LinkInMessage
import net.corda.p2p.LinkOutMessage
import net.corda.p2p.crypto.protocol.api.Session
import net.corda.p2p.linkmanager.LinkManagerNetworkMap

interface SessionManager: LifecycleWithDominoTile {
    fun processOutboundMessage(message: AuthenticatedMessageAndKey): SessionState
    fun getSessionById(uuid: String): SessionDirection
    fun processSessionMessage(message: LinkInMessage): LinkOutMessage?
    fun inboundSessionEstablished(sessionId: String)
    fun dataMessageSent(session: Session)
    fun messageAcknowledged(sessionId: String)

    //On the Outbound side there is a single unique session per SessionKey.
    data class SessionKey(
        val ourId: LinkManagerNetworkMap.HoldingIdentity,
        val responderId: LinkManagerNetworkMap.HoldingIdentity
    )

    sealed class SessionState {
        data class NewSessionNeeded(val sessionId: String, val sessionInitMessage: LinkOutMessage): SessionState()
        object SessionAlreadyPending: SessionState()
        data class SessionEstablished(val session: Session): SessionState()
        object CannotEstablishSession: SessionState()
    }

    sealed class SessionDirection {
        data class Inbound(val key: SessionKey, val session: Session): SessionDirection()
        data class Outbound(val key: SessionKey, val session: Session): SessionDirection()
        object NoSession: SessionDirection()
    }
}
