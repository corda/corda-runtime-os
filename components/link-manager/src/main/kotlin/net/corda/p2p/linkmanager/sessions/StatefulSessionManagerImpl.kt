package net.corda.p2p.linkmanager.sessions

import net.corda.crypto.client.SessionEncryptionOpsClient
import net.corda.data.p2p.AuthenticatedMessageAndKey
import net.corda.data.p2p.LinkInMessage
import net.corda.data.p2p.ReEstablishSessionMessage
import net.corda.data.p2p.app.AuthenticatedMessage
import net.corda.data.p2p.app.MembershipStatusFilter
import net.corda.data.p2p.event.SessionCreated
import net.corda.data.p2p.event.SessionDirection
import net.corda.data.p2p.event.SessionEvent
import net.corda.libs.configuration.SmartConfig
import net.corda.libs.statemanager.api.State
import net.corda.libs.statemanager.api.StateManager
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.LifecycleCoordinatorName
import net.corda.lifecycle.domino.logic.ComplexDominoTile
import net.corda.membership.read.MembershipGroupReaderProvider
import net.corda.messaging.api.records.Record
import net.corda.messaging.api.subscription.factory.SubscriptionFactory
import net.corda.p2p.crypto.protocol.api.AuthenticatedEncryptionSession
import net.corda.p2p.crypto.protocol.api.AuthenticatedSession
import net.corda.p2p.crypto.protocol.api.Session
import net.corda.p2p.linkmanager.common.MessageConverter.Companion.createLinkOutMessage
import net.corda.p2p.linkmanager.membership.calculateOutboundSessionKey
import net.corda.p2p.linkmanager.membership.getSessionCounterpartiesFromMessage
import net.corda.p2p.linkmanager.membership.lookup
import net.corda.p2p.linkmanager.sessions.SessionManager.SessionState.CannotEstablishSession
import net.corda.p2p.linkmanager.sessions.SessionManager.SessionState.NewSessionsNeeded
import net.corda.p2p.linkmanager.sessions.SessionManager.SessionState.SessionAlreadyPending
import net.corda.p2p.linkmanager.sessions.SessionManager.SessionState.SessionEstablished
import net.corda.p2p.linkmanager.sessions.events.StatefulSessionEventProcessor
import net.corda.p2p.linkmanager.sessions.lookup.SessionLookup
import net.corda.p2p.linkmanager.sessions.messages.SessionMessageProcessor
import net.corda.p2p.linkmanager.sessions.metadata.CommonMetadata
import net.corda.p2p.linkmanager.sessions.metadata.OutboundSessionMetadata
import net.corda.p2p.linkmanager.sessions.metadata.OutboundSessionMetadata.Companion.toOutbound
import net.corda.p2p.linkmanager.sessions.metadata.OutboundSessionStatus
import net.corda.p2p.linkmanager.sessions.metadata.toCounterparties
import net.corda.p2p.linkmanager.sessions.utils.OutboundMessageContext
import net.corda.p2p.linkmanager.sessions.utils.OutboundMessageResults
import net.corda.p2p.linkmanager.sessions.utils.OutboundMessageState
import net.corda.p2p.linkmanager.sessions.utils.SessionUtils.getSessionCounterpartiesFromState
import net.corda.p2p.linkmanager.sessions.writer.SessionWriter
import net.corda.p2p.linkmanager.state.SessionState
import net.corda.schema.Schemas
import net.corda.schema.registry.AvroSchemaRegistry
import net.corda.utilities.time.Clock
import net.corda.virtualnode.toCorda
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.time.Duration
import net.corda.data.p2p.crypto.InitiatorHandshakeMessage as AvroInitiatorHandshakeMessage
import net.corda.data.p2p.crypto.InitiatorHelloMessage as AvroInitiatorHelloMessage
import net.corda.data.p2p.crypto.ResponderHandshakeMessage as AvroResponderHandshakeMessage
import net.corda.data.p2p.crypto.ResponderHelloMessage as AvroResponderHelloMessage

@Suppress("LongParameterList")
internal class StatefulSessionManagerImpl(
    subscriptionFactory: SubscriptionFactory,
    messagingConfig: SmartConfig,
    coordinatorFactory: LifecycleCoordinatorFactory,
    stateManager: StateManager,
    //sessionEventListener: StatefulSessionEventProcessor,
    private val stateManagerWrapper: StateManagerWrapper,
    private val sessionManagerImpl: SessionManagerImpl,
    private val stateConvertor: StateConvertor,
    private val clock: Clock,
    private val membershipGroupReaderProvider: MembershipGroupReaderProvider,
    private val deadSessionMonitor: DeadSessionMonitor,
    private val schemaRegistry: AvroSchemaRegistry,
    private val sessionCache: SessionCache,
    private val sessionLookup: SessionLookup,
    private val sessionWriter: SessionWriter,
    private val sessionMessageProcessor: SessionMessageProcessor,
    private val stateFactory: StateFactory = StateFactory(stateConvertor),
) : SessionManager {
    companion object {
        const val LINK_MANAGER_SUBSYSTEM = "link-manager"
        private val SESSION_VALIDITY_PERIOD: Duration = Duration.ofDays(7)
        private val logger: Logger = LoggerFactory.getLogger(this::class.java.enclosingClass)
    }

    override fun <T> processOutboundMessages(
        wrappedMessages: Collection<T>,
        getMessage: (T) -> AuthenticatedMessageAndKey,
    ): Collection<Pair<T, SessionManager.SessionState>> {
        val messages =
            wrappedMessages.map {
                OutboundMessageContext(it, getMessage(it))
            }
        val keysToMessages =
            messages.groupBy {
                val messageHeader = it.message.message.header
                membershipGroupReaderProvider.calculateOutboundSessionKey(
                    messageHeader.source.toCorda(),
                    messageHeader.destination.toCorda(),
                    messageHeader.statusFilter,
                    messageHeader.messageId,
                )
            }

        val cachedSessions = sessionLookup.getCachedOutboundSessions(keysToMessages)

        val notInCache = (keysToMessages - cachedSessions.keys)
        val sessionStates =
            if (notInCache.isEmpty()) {
                return cachedSessions.values.flatten()
            } else {
                sessionLookup.getPersistedOutboundSessions(notInCache)
            }

        return processStateUpdates(sessionStates) + cachedSessions.values.flatten()
    }

    private fun <T> processOutboundMessagesState(
        state: OutboundMessageState<T>,
    ): Collection<OutboundMessageResults<T>> {
        if (state.key == null) {
            return state.toResults(
                CannotEstablishSession("State does not have a key, cannot establish sessions without keys."),
            )
        }
        val counterparties = state.messages.first().sessionCounterparties()
            ?: return state.toResults(
                CannotEstablishSession("Could not retrieve session counterparties for state with key " +
                        "'${state.key}'. Cannot establish sessions without counterparties."),
            )
        val metadata = state.stateManagerSessionState?.managerState?.metadata?.toOutbound()
        return if (metadata?.lastSendExpired(clock) == false) {
            when (metadata.status) {
                OutboundSessionStatus.SentInitiatorHello, OutboundSessionStatus.SentInitiatorHandshake -> {
                    state.toResults(
                        SessionAlreadyPending(counterparties),
                    )
                }

                OutboundSessionStatus.SessionReady -> {
                    handleReadySession(state, counterparties)
                }
            }
        } else {
            when (metadata?.status) {
                null -> {
                    newSessionNeeded(
                        counterparties,
                        state.first.message.message.header.statusFilter,
                    )?.let { (needed, newState) ->
                        state.toResultsFirstAndOther(
                            action = CreateAction(newState),
                            firstState = needed,
                            otherStates = SessionAlreadyPending(counterparties),
                        )
                    } ?: state.toResults(
                        CannotEstablishSession("Could not create new session needed for state with key " +
                                "'${state.key}'."),
                    )
                }

                OutboundSessionStatus.SentInitiatorHello, OutboundSessionStatus.SentInitiatorHandshake -> {
                    state.stateManagerSessionState
                        .replaySessionMessage(
                            state.first.message.message.header.statusFilter
                        )?.let { (needed, newState) ->
                        state.toResultsFirstAndOther(
                            action = UpdateAction(newState, true),
                            firstState = needed,
                            otherStates = SessionAlreadyPending(counterparties),
                        )
                    } ?: state.toResults(
                        CannotEstablishSession("Could not replay session message for state with key " +
                                "'${state.key}'."),
                    )
                }

                OutboundSessionStatus.SessionReady -> {
                    handleReadySession(state, counterparties)
                }
            }
        }
    }

    private fun <T> handleReadySession(
        state: OutboundMessageState<T>, counterparties: SessionManager.SessionCounterparties
    ): Collection<OutboundMessageResults<T>> {
        return state.stateManagerSessionState?.sessionState?.retrieveEstablishedSession(counterparties)?.let { established ->
            state.key?.let { key ->
                sessionWriter.cacheSession(
                    SessionDirection.OUTBOUND,
                    state.stateManagerSessionState.toCounterparties(),
                    established.session,
                    key,
                )
            }
            state.toResults(
                established,
            )
        } ?: state.toResults(
            CannotEstablishSession("Could not retrieve established session needed for state with key " +
                    "'${state.key}'."),
        )
    }

    override fun <T> getSessionsById(
        sessionIdAndMessages: Collection<T>,
        getSessionId: (T) -> String,
    ): Collection<Pair<T, SessionManager.SessionDirection>> {
        if (sessionIdAndMessages.isEmpty()) {
            return emptyList()
        }
        val traceable = sessionIdAndMessages.groupBy { getSessionId(it) }
        val allCached = sessionLookup.getAllCachedSessions(traceable)
        val sessionIdsNotInCache = (traceable - allCached.keys)
        val inboundSessionsFromStateManager = if (sessionIdsNotInCache.isEmpty()) {
            emptyList()
        } else {
            sessionLookup.getPersistedInboundSessions(sessionIdsNotInCache)
        }
        val sessionsIdsNotInInboundStateManager =
            (sessionIdsNotInCache.keys - inboundSessionsFromStateManager.map { it.second.session.sessionId }.toSet())
        val outboundSessionsFromStateManager = if (sessionsIdsNotInInboundStateManager.isEmpty()) {
            emptyList()
        } else {
            sessionLookup.getPersistedOutboundSessionsBySessionId(sessionsIdsNotInInboundStateManager, sessionIdsNotInCache)
        }
        val sessionsNotFound =
            (
                sessionsIdsNotInInboundStateManager - outboundSessionsFromStateManager.map { it.second.session.sessionId }
                    .toSet()
                ).mapNotNull { sessionId ->
                sessionIdsNotInCache[sessionId]?.let {
                    it to SessionManager.SessionDirection.NoSession
                }
            }

        return (allCached.values + inboundSessionsFromStateManager + outboundSessionsFromStateManager + sessionsNotFound)
            .flatMap { (traceables, direction) ->
                traceables.map { traceable ->
                    traceable to direction
                }
            }
    }

    override fun <T> processSessionMessages(
        wrappedMessages: Collection<T>,
        getMessage: (T) -> LinkInMessage,
    ): Collection<Pair<T, SessionManager.ProcessSessionMessagesResult>> {
        val messages = wrappedMessages.map { it to getMessage(it) }
        val results = sessionMessageProcessor.processInboundSessionMessages(messages) +
                sessionMessageProcessor.processOutboundSessionMessages(messages)

        val failedUpdate =
            stateManagerWrapper.upsert(results.mapNotNull { it.result?.stateAction }).keys

        return results.mapNotNull { result ->
            if (failedUpdate.contains(result.result?.stateAction?.state?.key)) {
                null
            } else {
                result
            }
        }.map { result ->
            val linkOutMessage = result.result?.message
            val sessionCreationRecords = when (linkOutMessage?.payload) {
                is AvroResponderHelloMessage, is AvroResponderHandshakeMessage -> {
                    result.result.sessionToCache?.let { sessionToCache ->
                        sessionWriter.cacheSession(
                            SessionDirection.INBOUND,
                            result.result.stateAction.state.toCounterparties(),
                            sessionToCache,
                        )
                        recordsForSessionCreated(sessionToCache.sessionId, SessionDirection.INBOUND)
                    } ?: emptyList()
                }
                is AvroInitiatorHelloMessage, is AvroInitiatorHandshakeMessage, null -> {
                    result.result?.sessionToCache?.let { sessionToCache ->
                        val key = result.result.stateAction.state.key
                        sessionWriter.cacheSession(
                            SessionDirection.OUTBOUND,
                            result.result.stateAction.state.toCounterparties(),
                            sessionToCache,
                            key,
                        )
                        recordsForSessionCreated(key, SessionDirection.OUTBOUND)
                    } ?: emptyList()
                }
                else -> emptyList()
            }
            result.traceable to SessionManager.ProcessSessionMessagesResult(linkOutMessage, sessionCreationRecords)
        }
    }

    override fun messageAcknowledged(sessionId: String) {
        deadSessionMonitor.ackReceived(sessionId)
    }

    override fun dataMessageSent(session: Session) {
        deadSessionMonitor.messageSent(session.sessionId)
    }

    override fun deleteOutboundSession(
        counterParties: SessionManager.Counterparties,
        message: AuthenticatedMessage,
    ) {
        val sessionId = try {
            schemaRegistry.deserialize(
                message.payload,
                ReEstablishSessionMessage::class.java,
                null,
            ).sessionId
        } catch (e: Exception) {
            logger.warn("Could not deserialize '{}'. Outbound session will not be deleted.", ReEstablishSessionMessage::class.simpleName)
            return
        }

        val key = sessionCache.getKeyForOutboundSessionId(sessionId) ?: membershipGroupReaderProvider.calculateOutboundSessionKey(
            counterParties.ourId,
            counterParties.counterpartyId,
            message.header.statusFilter,
            message.header.messageId,
        )

        deadSessionMonitor.sessionRemoved(sessionId)

        if (key == null) {
            logger.warn("Could not delete outbound session '{}' lost by counterparty.", sessionId)
            return
        }

        sessionCache.deleteByKey(key)
    }

    private fun <T> OutboundMessageContext<T>.sessionCounterparties() =
        membershipGroupReaderProvider.getSessionCounterpartiesFromMessage(message.message)

    private fun SessionState.retrieveEstablishedSession(
        counterParties: SessionManager.SessionCounterparties,
    ): SessionEstablished? {
        return when (val sessionData = this.sessionData) {
            is AuthenticatedSession, is AuthenticatedEncryptionSession ->
                SessionEstablished(sessionData as Session, counterParties)

            else -> null
        }
    }

    private fun newSessionNeeded(
        counterParties: SessionManager.SessionCounterparties,
        filter: MembershipStatusFilter,
    ): Pair<NewSessionsNeeded, State>? {
        val initMessage = sessionManagerImpl.genSessionInitMessages(counterParties, 1).firstOrNull() ?: return null
        val message =
            sessionManagerImpl.linkOutMessagesFromSessionInitMessages(
                counterParties,
                listOf(initMessage),
                filter,
            )?.firstOrNull() ?: return null

        val timestamp = clock.instant()
        val newMetadata =
            OutboundSessionMetadata(
                sessionId = initMessage.first.sessionId,
                commonData = CommonMetadata(
                    source = counterParties.ourId,
                    destination = counterParties.counterpartyId,
                    lastSendTimestamp = timestamp,
                    expiry = timestamp + SESSION_VALIDITY_PERIOD,
                ),
                status = OutboundSessionStatus.SentInitiatorHello,
                serial = counterParties.serial,
                membershipStatus = counterParties.status,
                communicationWithMgm = counterParties.communicationWithMgm,
                initiationTimestamp = timestamp,
            )
        val newState = stateFactory.createState(
            key = calculateOutboundSessionKey(
                counterParties.ourId,
                counterParties.counterpartyId,
                counterParties.serial,
            ),
            sessionState = SessionState(message.second, initMessage.first),
            metadata = newMetadata.toMetadata(),
        )
        return NewSessionsNeeded(listOf(message), counterParties) to newState
    }

    private fun StateManagerWrapper.StateManagerSessionState.replaySessionMessage(
        statusFilter: MembershipStatusFilter,
    ): Pair<NewSessionsNeeded, State>? {
        val previousSessionMessage = this.sessionState.message ?: return null
        val previousHeader = previousSessionMessage.header
        val outboundMetadata = this.managerState.metadata.toOutbound()
        val linkOutMessage = membershipGroupReaderProvider.lookup(
            previousHeader.sourceIdentity.toCorda(),
            previousHeader.destinationIdentity.toCorda(),
            statusFilter
        )?.let {
            createLinkOutMessage(
                previousSessionMessage.payload,
                previousHeader.sourceIdentity.toCorda(),
                it,
                previousHeader.destinationNetworkType
            )
        } ?: return null.also {
            logger.warn(
                "Attempted to resend a session negotiation message (type " +
                        "'${previousSessionMessage.payload::class.java.simpleName}') for session with ID " +
                        "'${outboundMetadata.sessionId}' between '${outboundMetadata.commonData.source}' and peer " +
                        "'${outboundMetadata.commonData.destination}' with status '$statusFilter', " +
                        "but could not construct LinkOutMessage. The message was not resent."
            )
        }
        val updatedMetadata = outboundMetadata.copy(
            commonData = outboundMetadata.commonData.copy(
                lastSendTimestamp = clock.instant(),
            ),
        )
        val updatedState = stateFactory.createState(this.managerState, updatedMetadata.toMetadata())
        return NewSessionsNeeded(
            listOf(updatedMetadata.sessionId to linkOutMessage),
            getSessionCounterpartiesFromState(updatedState),
        ) to updatedState
    }

    private fun <T> processStateUpdates(
        sessionStates: Collection<OutboundMessageState<T>>,
    ): Collection<Pair<T, SessionManager.SessionState>> {
        val resultStates = sessionStates.flatMap { state -> processOutboundMessagesState(state) }
        val updates = resultStates.mapNotNull { it.action }
        val failedUpdates = stateManagerWrapper.upsert(updates)

        return resultStates.flatMap { resultState ->
            val key = resultState.key
            if (failedUpdates.containsKey(key)) {
                val savedState = failedUpdates[key]
                val savedMetadata = savedState?.metadata?.toOutbound()
                val newState = when (savedMetadata?.status) {
                    OutboundSessionStatus.SentInitiatorHello, OutboundSessionStatus.SentInitiatorHandshake ->
                        resultState.messages.first().sessionCounterparties()?.let {
                            SessionAlreadyPending(it)
                        } ?: CannotEstablishSession("Could not retrieve session counterparties for state with key " +
                                "'$key'. Cannot establish sessions without counterparties.")
                    OutboundSessionStatus.SessionReady -> resultState.messages.first().sessionCounterparties()?.let {
                        val session = stateConvertor.toCordaSessionState(
                            savedState,
                            sessionManagerImpl.revocationCheckerClient::checkRevocation,
                        )
                        session?.retrieveEstablishedSession(it)
                    } ?: CannotEstablishSession("Could not retrieve established session needed for state " +
                            "with key '$key'.")
                    null -> CannotEstablishSession("Cannot process state update as state with key " +
                            "'$key' had null as new status.")
                }
                resultState.messages.map { it.trace to newState }
            } else {
                resultState.messages.map { it.trace to resultState.sessionState }
            }
        }
    }

    private fun recordsForSessionCreated(key: String, direction: SessionDirection): List<Record<String, SessionEvent>> {
        return listOf(Record(Schemas.P2P.SESSION_EVENTS, key, SessionEvent(SessionCreated(direction, key))))
    }

    private val sessionEventListener = StatefulSessionEventProcessor(
        coordinatorFactory,
        subscriptionFactory,
        messagingConfig,
        stateManager,
        stateConvertor,
        sessionCache,
        sessionManagerImpl,
    )

    override val dominoTile =
        ComplexDominoTile(
            this::class.java.simpleName,
            coordinatorFactory,
            dependentChildren =
            setOf(
                stateManager.name,
                sessionManagerImpl.dominoTile.coordinatorName,
                LifecycleCoordinatorName.forComponent<SessionEncryptionOpsClient>(),
                sessionEventListener.dominoTile.coordinatorName,
                sessionLookup.dominoTile.coordinatorName,
                sessionMessageProcessor.dominoTile.coordinatorName,
            ),
            managedChildren =
            setOf(
                sessionManagerImpl.dominoTile.toNamedLifecycle(),
                sessionEventListener.dominoTile.toNamedLifecycle(),
                sessionLookup.dominoTile.toNamedLifecycle(),
                sessionMessageProcessor.dominoTile.toNamedLifecycle(),
            ),
        )
}
