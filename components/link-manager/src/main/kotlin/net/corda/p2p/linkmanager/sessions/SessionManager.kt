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

    data class SessionCounterparties(
        val ourId: LinkManagerNetworkMap.HoldingIdentity,
        val counterpartyId: LinkManagerNetworkMap.HoldingIdentity
    )

    sealed class SessionState {
        data class NewSessionsNeeded(val messages: List<Pair<String, LinkOutMessage>>): SessionState()
        object SessionAlreadyPending: SessionState()
        data class SessionEstablished(val session: Session): SessionState()
        object CannotEstablishSession: SessionState()
    }

    sealed class SessionDirection {
        data class Inbound(val counterparties: SessionCounterparties, val session: Session): SessionDirection()
        data class Outbound(val counterparties: SessionCounterparties, val session: Session): SessionDirection()
        object NoSession: SessionDirection()
    }
}
