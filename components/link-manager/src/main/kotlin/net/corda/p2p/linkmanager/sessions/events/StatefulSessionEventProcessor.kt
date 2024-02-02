package net.corda.p2p.linkmanager.sessions.events

import java.util.UUID
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Future
import net.corda.data.p2p.event.SessionCreated
import net.corda.data.p2p.event.SessionDeleted
import net.corda.data.p2p.event.SessionDirection
import net.corda.data.p2p.event.SessionEvent
import net.corda.libs.configuration.SmartConfig
import net.corda.libs.statemanager.api.StateManager
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.domino.logic.LifecycleWithDominoTile
import net.corda.lifecycle.domino.logic.util.SubscriptionDominoTile
import net.corda.messaging.api.processor.PubSubProcessor
import net.corda.messaging.api.records.Record
import net.corda.messaging.api.subscription.config.SubscriptionConfig
import net.corda.messaging.api.subscription.factory.SubscriptionFactory
import net.corda.p2p.crypto.protocol.api.Session
import net.corda.p2p.linkmanager.sessions.SessionCache
import net.corda.p2p.linkmanager.sessions.SessionManager
import net.corda.p2p.linkmanager.sessions.SessionManagerImpl
import net.corda.p2p.linkmanager.sessions.StateConvertor
import net.corda.p2p.linkmanager.sessions.metadata.CommonMetadata.Companion.toCommonMetadata
import net.corda.p2p.linkmanager.sessions.metadata.InboundSessionMetadata.Companion.toInbound
import net.corda.p2p.linkmanager.sessions.metadata.InboundSessionStatus
import net.corda.p2p.linkmanager.sessions.metadata.OutboundSessionMetadata.Companion.toOutbound
import net.corda.p2p.linkmanager.sessions.metadata.OutboundSessionStatus
import net.corda.schema.Schemas
import org.slf4j.LoggerFactory

@Suppress("LongParameterList")
internal class StatefulSessionEventProcessor(
    coordinatorFactory: LifecycleCoordinatorFactory,
    subscriptionFactory: SubscriptionFactory,
    messagingConfiguration: SmartConfig,
    private val stateManager: StateManager,
    private val stateConvertor: StateConvertor,
    private val sessionExpiryScheduler: SessionCache,
    private val sessionManagerImpl: SessionManagerImpl,
): LifecycleWithDominoTile {

    companion object {
        private val CONSUMER_GROUP_ID = "session-events" + UUID.randomUUID().toString()
    }

    private val subscriptionConfig = SubscriptionConfig(CONSUMER_GROUP_ID, Schemas.P2P.SESSION_EVENTS)
    private val sessionPartitionSubscription = {
        subscriptionFactory.createPubSubSubscription(
            subscriptionConfig,
            SessionEventProcessor(stateManager, stateConvertor, sessionManagerImpl, sessionExpiryScheduler),
            messagingConfiguration
        )
    }

    class SessionEventProcessor(
        private val stateManager: StateManager,
        private val stateConvertor: StateConvertor,
        private val sessionManagerImpl: SessionManagerImpl,
        private val sessionCache: SessionCache,
    ) : PubSubProcessor<String, SessionEvent> {
        private val logger = LoggerFactory.getLogger(this::class.java)
        override fun onNext(event: Record<String, SessionEvent>): Future<Unit> {
            val sessionEvent = event.value
            when (val type = sessionEvent?.type) {
                is SessionCreated -> {
                    sessionsCreated(listOf(type))
                }
                is SessionDeleted -> {
                    logger.info("Received a session deletion event for session with key ${type.stateManagerKey}.")
                    sessionCache.removeFromScheduler(type.stateManagerKey)
                    sessionCache.invalidate(type.stateManagerKey)
                }
                null -> {
                    logger.warn("Received an unknown session event. This will be ignored.")
                }
            }
            return CompletableFuture.completedFuture(Unit)
        }

        private fun sessionsCreated(events: List<SessionCreated>) {
            val notCachedSessions = events.filter { sessionCache.getByKeyIfCached(it.stateManagerKey) != null }
            val states = stateManager.get(notCachedSessions.mapNotNull { it.stateManagerKey }.toList())
            for (event in events) {
                val state = states[event.stateManagerKey]
                if (state == null) {
                    logger.info("Received a ${event.direction} session created event for ${event.stateManagerKey} but no session exists " +
                            "in the state manager.")
                    return
                }
                val session = stateConvertor.toCordaSessionState(
                    state,
                    sessionManagerImpl.revocationCheckerClient::checkRevocation,
                ).sessionData as? Session
                if (session == null) {
                    logger.error("Received a ${event.direction} session created event for ${event.stateManagerKey} but could not " +
                            "deserialize the session.")
                    return
                }
                val metadata = state.metadata.toCommonMetadata()
                val counterparties = SessionManager.Counterparties(metadata.source, metadata.destination)

                when (event.direction) {
                    SessionDirection.INBOUND -> {
                        if (state.metadata.toInbound().status == InboundSessionStatus.SentResponderHandshake) {
                            logger.info(
                                "Received an inbound session creation event for session between (local=${counterparties.ourId} and " +
                                    "remote=${counterparties.counterpartyId}) for sessionId = ${session.sessionId}."
                            )
                            sessionCache.putInboundSession(
                                event.stateManagerKey,
                                SessionManager.SessionDirection.Inbound(counterparties, session)
                            )
                        } else {
                            logger.error(
                                "Received an inbound session creation event for session between (local=${counterparties.ourId} and " +
                                        "remote=${counterparties.counterpartyId}) for sessionId = ${session.sessionId} but session " +
                                        "negotiation is not complete."
                            )
                        }
                    }

                    SessionDirection.OUTBOUND -> {
                        if (state.metadata.toOutbound().status == OutboundSessionStatus.SessionReady) {
                            logger.info(
                                "Received an outbound session creation event for session between (local=${counterparties.ourId} and " +
                                    "remote=${counterparties.counterpartyId}) for sessionId = ${session.sessionId}."
                            )
                            sessionCache.putOutboundSession(
                                event.stateManagerKey,
                                counterparties,
                                session
                            )
                        } else {
                            logger.error(
                                "Received an outbound session creation event for session between (local=${counterparties.ourId} and " +
                                    "remote=${counterparties.counterpartyId}) for sessionId = ${session.sessionId} but session " +
                                    "negotiation is not complete."
                            )
                        }
                    }
                    null -> {
                        logger.error("Received an session creation event with no direction for session between " +
                                "(local=${counterparties.ourId} and remote=${counterparties.counterpartyId}) for sessionId = " +
                                "${session.sessionId}.")
                    }
                }
            }
        }

        override val keyClass: Class<String> = String::class.java
        override val valueClass: Class<SessionEvent> = SessionEvent::class.java

    }

    override val dominoTile = SubscriptionDominoTile(
        coordinatorFactory,
        sessionPartitionSubscription,
        subscriptionConfig,
        emptySet(),
        emptySet()
    )
}