package net.corda.p2p.linkmanager.sessions

import net.corda.data.p2p.AuthenticatedMessageAndKey
import net.corda.lifecycle.domino.logic.LifecycleWithDominoTile
import net.corda.data.p2p.LinkInMessage
import net.corda.data.p2p.LinkOutMessage
import net.corda.data.p2p.app.AuthenticatedMessage
import net.corda.data.p2p.app.MembershipStatusFilter
import net.corda.p2p.crypto.protocol.api.Session
import net.corda.virtualnode.HoldingIdentity

internal interface SessionManager : LifecycleWithDominoTile {
    fun <T>processOutboundMessages(
        wrappedMessages: Collection<T>,
        getMessage: (T) -> AuthenticatedMessageAndKey
    ): Collection<Pair<T, SessionState>>
    fun <T>getSessionsById(uuids: Collection<T>, getSessionId: (T) -> String): Collection<Pair<T, SessionDirection>>
    fun <T>processSessionMessages(wrappedMessages: Collection<T>, getMessage: (T) -> LinkInMessage): Collection<Pair<T, LinkOutMessage?>>
    fun inboundSessionEstablished(sessionId: String)
    fun messageAcknowledged(sessionId: String)
    fun dataMessageReceived(sessionId: String, source: HoldingIdentity, destination: HoldingIdentity)
    fun dataMessageSent(session: Session)
    fun deleteOutboundSession(counterParties: Counterparties, message: AuthenticatedMessage)

    data class SessionCounterparties(
        override val ourId: HoldingIdentity,
        override val counterpartyId: HoldingIdentity,
        val status: MembershipStatusFilter,
        val serial: Long,
        val communicationWithMgm: Boolean,
    ): BaseCounterparties

    data class Counterparties(
        override val ourId: HoldingIdentity,
        override val counterpartyId: HoldingIdentity,
    ): BaseCounterparties {
        fun reverse() = Counterparties(ourId = counterpartyId, counterpartyId = ourId)
    }

    interface BaseCounterparties {
        val ourId: HoldingIdentity
        val counterpartyId: HoldingIdentity
    }

    sealed class SessionState {
        data class NewSessionsNeeded(val messages: List<Pair<String, LinkOutMessage>>,
                                     val sessionCounterparties: SessionCounterparties) : SessionState()
        data class SessionAlreadyPending(val sessionCounterparties: SessionCounterparties) : SessionState()
        data class SessionEstablished(val session: Session,
                                      val sessionCounterparties: SessionCounterparties) : SessionState()
        object CannotEstablishSession : SessionState()
    }

    sealed class SessionDirection {
        data class Inbound(val counterparties: Counterparties, val session: Session) : SessionDirection()
        data class Outbound(val counterparties: Counterparties, val session: Session) : SessionDirection()
        object NoSession : SessionDirection()
    }
}