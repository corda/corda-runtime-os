package net.corda.p2p.linkmanager.sessions

import net.corda.data.p2p.ReEstablishSessionMessage
import net.corda.data.p2p.app.MembershipStatusFilter
import net.corda.libs.statemanager.api.State
import net.corda.p2p.linkmanager.sessions.metadata.OutboundSessionMetadata.Companion.isOutbound
import net.corda.p2p.linkmanager.sessions.metadata.OutboundSessionMetadata.Companion.toOutbound
import net.corda.p2p.linkmanager.sessions.metadata.toCounterparties
import net.corda.p2p.messaging.P2pRecordsFactory
import net.corda.p2p.messaging.Subsystem
import net.corda.virtualnode.toAvro
import org.slf4j.LoggerFactory

internal class ReEstablishmentMessageSender(
    private val p2pRecordsFactory: P2pRecordsFactory,
    sessionManagerImpl: SessionManagerImpl,
) {
    companion object {
        private val logger = LoggerFactory.getLogger(ReEstablishmentMessageSender::class.java)
    }

    private val publisher by lazy {
        sessionManagerImpl.publisher
    }
    fun send(
        state: State,
    ) {
        val sessionId = if (state.metadata.isOutbound()) {
            state.metadata.toOutbound().sessionId
        } else {
            state.key
        }
        val counterparties = state.toCounterparties()
        val source = counterparties.ourId
        val destination = counterparties.counterpartyId
        val record = p2pRecordsFactory.createAuthenticatedMessageRecord(
            source = source.toAvro(),
            destination = destination.toAvro(),
            content = ReEstablishSessionMessage(sessionId),
            subsystem = Subsystem.LINK_MANAGER,
            filter = MembershipStatusFilter.ACTIVE,
        )
        logger.info("Sending '{}' to session initiator '{}'.", ReEstablishSessionMessage::class.simpleName, destination)
        publisher.publish(listOf(record))
    }
}
