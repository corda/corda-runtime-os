package net.corda.p2p.linkmanager.sessions

import net.corda.data.identity.HoldingIdentity
import net.corda.lifecycle.domino.logic.LifecycleWithDominoTile
import net.corda.messaging.api.records.Record
import net.corda.p2p.AuthenticatedMessageAndKey
import net.corda.p2p.LinkInMessage
import net.corda.p2p.LinkOutMessage
import net.corda.p2p.crypto.protocol.api.Session
import net.corda.p2p.linkmanager.LinkManager
import net.corda.p2p.linkmanager.LinkManagerGroupPolicyProvider
import net.corda.p2p.linkmanager.LinkManagerMembershipGroupReader
import net.corda.p2p.linkmanager.messaging.MessageConverter
import net.corda.schema.Schemas

internal interface SessionManager : LifecycleWithDominoTile {
    fun processOutboundMessage(message: AuthenticatedMessageAndKey): SessionState
    fun getSessionById(uuid: String): SessionDirection
    fun processSessionMessage(message: LinkInMessage): LinkOutMessage?
    fun inboundSessionEstablished(sessionId: String)
    fun dataMessageSent(session: Session)
    fun messageAcknowledged(sessionId: String)

    data class SessionCounterparties(
        val ourId: HoldingIdentity,
        val counterpartyId: HoldingIdentity
    )

    sealed class SessionState {
        data class NewSessionsNeeded(val messages: List<Pair<String, LinkOutMessage>>) : SessionState()
        object SessionAlreadyPending : SessionState()
        data class SessionEstablished(val session: Session) : SessionState()
        object CannotEstablishSession : SessionState()
    }

    sealed class SessionDirection {
        data class Inbound(val counterparties: SessionCounterparties, val session: Session) : SessionDirection()
        data class Outbound(val counterparties: SessionCounterparties, val session: Session) : SessionDirection()
        object NoSession : SessionDirection()
    }

}
internal fun SessionManager.recordsForSessionEstablished(
    groups: LinkManagerGroupPolicyProvider,
    members: LinkManagerMembershipGroupReader,
    session: Session,
    messageAndKey: AuthenticatedMessageAndKey
): List<Record<String, *>> {
    val records = mutableListOf<Record<String, *>>()
    val key = LinkManager.generateKey()
    dataMessageSent(session)
    MessageConverter.linkOutMessageFromAuthenticatedMessageAndKey(messageAndKey, session, groups, members)?.let {
        records.add(Record(Schemas.P2P.LINK_OUT_TOPIC, key, it))
    }
    return records
}
