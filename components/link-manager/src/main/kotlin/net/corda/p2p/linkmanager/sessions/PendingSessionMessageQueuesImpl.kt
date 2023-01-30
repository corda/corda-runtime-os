package net.corda.p2p.linkmanager.sessions

import net.corda.data.p2p.AuthenticatedMessageAndKey
import net.corda.libs.configuration.SmartConfig
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.domino.logic.util.PublisherWithDominoLogic
import net.corda.messaging.api.publisher.config.PublisherConfig
import net.corda.messaging.api.publisher.factory.PublisherFactory
import net.corda.messaging.api.records.Record
import net.corda.p2p.crypto.protocol.api.Session
import net.corda.v5.base.util.debug
import org.slf4j.LoggerFactory
import java.util.LinkedList
import java.util.Queue

internal class PendingSessionMessageQueuesImpl(
    publisherFactory: PublisherFactory,
    coordinatorFactory: LifecycleCoordinatorFactory,
    messagingConfiguration: SmartConfig
) : PendingSessionMessageQueues {

    companion object {
        private val logger = LoggerFactory.getLogger(this::class.java.enclosingClass)
        private const val LINK_MANAGER_PUBLISHER_CLIENT_ID = "pending_session_messages_publisher"
    }

    private val queuedMessagesPendingSession =
        HashMap<SessionManager.SessionCounterparties, Queue<AuthenticatedMessageAndKey>>()
    private val publisher = PublisherWithDominoLogic(
        publisherFactory,
        coordinatorFactory,
        PublisherConfig(LINK_MANAGER_PUBLISHER_CLIENT_ID, false),
        messagingConfiguration
    )
    override val dominoTile = publisher.dominoTile

    /**
     * Either adds a [FlowMessage] to a queue for a session which is pending (has started but hasn't finished
     * negotiation with the destination) or adds the message to a new queue if we need to negotiate a new session.
     */
    override fun queueMessage(message: AuthenticatedMessageAndKey) {
        val counterparties = SessionManagerImpl.getSessionCounterpartiesFromMessage(message.message)
        val oldQueue = queuedMessagesPendingSession.putIfAbsent(counterparties, LinkedList())
        if (oldQueue != null) {
            oldQueue.add(message)
        } else {
            queuedMessagesPendingSession[counterparties]?.add(message)
        }
    }

    /**
     * Publish all the queued [FlowMessage]s to the P2P_OUT_TOPIC.
     */
    override fun sessionNegotiatedCallback(
        sessionManager: SessionManager,
        counterparties: SessionManager.SessionCounterparties,
        session: Session,
    ) {
        publisher.withLifecycleLock {
            if (!isRunning) {
                throw IllegalStateException("sessionNegotiatedCallback was called before the PendingSessionMessageQueues was started.")
            }
            val queuedMessages = queuedMessagesPendingSession[counterparties] ?: return@withLifecycleLock
            val records = mutableListOf<Record<String, *>>()
            while (queuedMessages.isNotEmpty()) {
                val message = queuedMessages.poll()
                logger.debug {
                    "Sending queued message ${message.message.header.messageId} " +
                        "to newly established session ${session.sessionId} with ${counterparties.counterpartyId}"
                }
                records.addAll(sessionManager.recordsForSessionEstablished(session, message))
            }
            publisher.publish(records)
        }
    }

    override fun destroyQueue(counterparties: SessionManager.SessionCounterparties) {
        queuedMessagesPendingSession.remove(counterparties)
    }

    override fun destroyAllQueues() {
        queuedMessagesPendingSession.clear()
    }
}
