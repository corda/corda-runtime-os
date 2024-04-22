package net.corda.p2p.linkmanager.sessions

import net.corda.data.p2p.ReEstablishSessionMessage
import net.corda.libs.statemanager.api.State
import net.corda.p2p.linkmanager.sessions.metadata.toCounterparties
import net.corda.p2p.messaging.P2pRecordsFactory
import net.corda.p2p.messaging.Subsystem
import net.corda.virtualnode.toAvro
import org.slf4j.LoggerFactory

internal class ReEstablishmentMessageSender(
    private val p2pRecordsFactory: P2pRecordsFactory,
    sessionMessageHelper: SessionMessageHelper,
) {
    companion object {
        private val logger = LoggerFactory.getLogger(ReEstablishmentMessageSender::class.java)
    }

    private val publisher by lazy {
        sessionMessageHelper.publisher
    }
    fun send(
        state: State,
    ) {
        val sessionId = state.key
        val counterparties = state.toCounterparties()
        val source = counterparties.ourId
        val destination = counterparties.counterpartyId
        val record = p2pRecordsFactory.createAuthenticatedMessageRecord(
            source = source.toAvro(),
            destination = destination.toAvro(),
            content = ReEstablishSessionMessage(sessionId),
            subsystem = Subsystem.LINK_MANAGER,
        )
        logger.info("Sending '{}' to session initiator '{}'.", ReEstablishSessionMessage::class.simpleName, destination)
        publisher.publish(listOf(record))
    }
}
