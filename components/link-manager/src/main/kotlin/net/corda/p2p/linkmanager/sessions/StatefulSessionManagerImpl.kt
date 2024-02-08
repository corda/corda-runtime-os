package net.corda.p2p.linkmanager.sessions

import net.corda.crypto.client.SessionEncryptionOpsClient
import net.corda.crypto.core.SecureHashImpl
import net.corda.crypto.core.bytes
import net.corda.data.p2p.AuthenticatedMessageAndKey
import net.corda.data.p2p.LinkInMessage
import net.corda.data.p2p.LinkOutMessage
import net.corda.data.p2p.ReEstablishSessionMessage
import net.corda.data.p2p.app.AppMessage
import net.corda.data.p2p.app.AuthenticatedMessage
import net.corda.data.p2p.app.AuthenticatedMessageHeader
import net.corda.data.p2p.app.MembershipStatusFilter
import net.corda.libs.statemanager.api.MetadataFilter
import net.corda.libs.statemanager.api.Operation
import net.corda.libs.statemanager.api.State
import net.corda.libs.statemanager.api.StateManager
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.LifecycleCoordinatorName
import net.corda.lifecycle.domino.logic.ComplexDominoTile
import net.corda.membership.read.MembershipGroupReaderProvider
import net.corda.messaging.api.records.Record
import net.corda.p2p.crypto.protocol.api.AuthenticatedEncryptionSession
import net.corda.p2p.crypto.protocol.api.AuthenticatedSession
import net.corda.p2p.crypto.protocol.api.AuthenticationProtocolInitiator
import net.corda.p2p.crypto.protocol.api.AuthenticationProtocolResponder
import net.corda.p2p.crypto.protocol.api.Session
import net.corda.p2p.linkmanager.membership.lookup
import net.corda.p2p.linkmanager.sessions.SessionManager.SessionState.CannotEstablishSession
import net.corda.p2p.linkmanager.sessions.SessionManager.SessionState.NewSessionsNeeded
import net.corda.p2p.linkmanager.sessions.SessionManager.SessionState.SessionAlreadyPending
import net.corda.p2p.linkmanager.sessions.SessionManager.SessionState.SessionEstablished
import net.corda.p2p.linkmanager.sessions.SessionManagerWarnings.alreadySessionWarning
import net.corda.p2p.linkmanager.sessions.SessionManagerWarnings.noSessionWarning
import net.corda.p2p.linkmanager.sessions.metadata.CommonMetadata
import net.corda.p2p.linkmanager.sessions.metadata.CommonMetadata.Companion.toCommonMetadata
import net.corda.p2p.linkmanager.sessions.metadata.InboundSessionMetadata
import net.corda.p2p.linkmanager.sessions.metadata.InboundSessionMetadata.Companion.toInbound
import net.corda.p2p.linkmanager.sessions.metadata.InboundSessionStatus
import net.corda.p2p.linkmanager.sessions.metadata.OutboundSessionMetadata
import net.corda.p2p.linkmanager.sessions.metadata.OutboundSessionMetadata.Companion.toOutbound
import net.corda.p2p.linkmanager.sessions.metadata.OutboundSessionStatus
import net.corda.p2p.linkmanager.state.SessionState
import net.corda.schema.Schemas.P2P.P2P_OUT_TOPIC
import net.corda.schema.registry.AvroSchemaRegistry
import net.corda.utilities.time.Clock
import net.corda.v5.crypto.DigestAlgorithmName
import net.corda.virtualnode.HoldingIdentity
import net.corda.virtualnode.toAvro
import net.corda.virtualnode.toCorda
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.nio.ByteBuffer
import java.security.MessageDigest
import java.time.Duration
import java.util.Base64
import net.corda.data.p2p.event.SessionDirection
import net.corda.libs.configuration.SmartConfig
import net.corda.messaging.api.publisher.factory.PublisherFactory
import net.corda.messaging.api.subscription.factory.SubscriptionFactory
import net.corda.p2p.linkmanager.sessions.events.StatefulSessionEventProcessor
import net.corda.p2p.linkmanager.sessions.events.StatefulSessionEventPublisher
import java.util.UUID
import net.corda.data.p2p.crypto.InitiatorHandshakeMessage as AvroInitiatorHandshakeMessage
import net.corda.data.p2p.crypto.InitiatorHelloMessage as AvroInitiatorHelloMessage
import net.corda.data.p2p.crypto.ResponderHandshakeMessage as AvroResponderHandshakeMessage
import net.corda.data.p2p.crypto.ResponderHelloMessage as AvroResponderHelloMessage

@Suppress("TooManyFunctions", "LongParameterList", "LargeClass")
internal class StatefulSessionManagerImpl(
    publisherFactory: PublisherFactory,
    subscriptionFactory: SubscriptionFactory,
    messagingConfig: SmartConfig,
    coordinatorFactory: LifecycleCoordinatorFactory,
    private val stateManager: StateManager,
    private val sessionManagerImpl: SessionManagerImpl,
    private val stateConvertor: StateConvertor,
    private val clock: Clock,
    private val membershipGroupReaderProvider: MembershipGroupReaderProvider,
    private val schemaRegistry: AvroSchemaRegistry,
) : SessionManager {
    companion object {
        const val LINK_MANAGER_SUBSYSTEM = "link-manager"
        private val SESSION_VALIDITY_PERIOD: Duration = Duration.ofDays(7)
        private val logger: Logger = LoggerFactory.getLogger(StatefulSessionManagerImpl::class.java)
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
                val serial =
                    getCounterpartySerial(
                        messageHeader.source.toCorda(),
                        messageHeader.destination.toCorda(),
                        messageHeader.statusFilter,
                    )
                if (serial == null) {
                    logger.warn(
                        "Cannot establish session for message ${messageHeader.messageId}: Failed to look up counterparty.",
                    )
                    null
                } else {
                    calculateOutboundSessionKey(
                        messageHeader.source.toCorda(),
                        messageHeader.destination.toCorda(),
                        serial,
                    )
                }
            }

        val cachedSessions = getCachedOutboundSessions(keysToMessages)

        val notInCache = (keysToMessages - cachedSessions.keys)
        val sessionStates =
            if (notInCache.isNotEmpty()) {
                sessionCache.validateStatesAndScheduleExpiry(
                    stateManager.get(notInCache.keys.filterNotNull()),
                ).let { states ->
                    notInCache.map { (id, items) ->
                        OutboundMessageState(
                            id,
                            states[id],
                            items,
                        )
                    }
                }
            } else {
                val messagesWithoutKey = keysToMessages[null] ?: return cachedSessions.values.flatten()
                listOf(
                    OutboundMessageState(
                        null,
                        null,
                        messagesWithoutKey,
                    ),
                )
            }
        val resultStates =
            sessionStates.flatMap { state ->
                processOutboundMessagesState(state)
            }

        return processStateUpdates(resultStates) + cachedSessions.values.flatten()
    }

    private fun <T> processOutboundMessagesState(
        state: OutboundMessageState<T>,
    ): Collection<OutboundMessageResults<T>> {
        if (state.key == null) {
            return state.toResults(
                CannotEstablishSession,
            )
        }
        val counterparties = state.messages.first().sessionCounterparties()
            ?: return state.toResults(
                CannotEstablishSession,
            )
        val metadata = state.state?.metadata?.toOutbound()
        return if (metadata?.lastSendExpired(clock) == false) {
            when (metadata.status) {
                OutboundSessionStatus.SentInitiatorHello, OutboundSessionStatus.SentInitiatorHandshake -> {
                    state.toResults(
                        SessionAlreadyPending(counterparties),
                    )
                }
                OutboundSessionStatus.SessionReady -> {
                    state.state.retrieveEstablishedSession(counterparties)?.let { establishedState ->
                        sessionCache.putOutboundSession(
                            state.key,
                            SessionManager.SessionDirection.Outbound(state.state.toCounterparties(), establishedState.session)
                        )
                        state.toResults(establishedState)
                    } ?: state.toResults(CannotEstablishSession)
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
                        CannotEstablishSession,
                    )
                }

                OutboundSessionStatus.SentInitiatorHello, OutboundSessionStatus.SentInitiatorHandshake -> {
                    state.state.replaySessionMessage()?.let { (needed, newState) ->
                        state.toResultsFirstAndOther(
                            action = UpdateAction(newState),
                            firstState = needed,
                            otherStates = SessionAlreadyPending(counterparties),
                        )
                    } ?: state.toResults(
                        CannotEstablishSession,
                    )
                }

                OutboundSessionStatus.SessionReady -> {
                    state.state.retrieveEstablishedSession(counterparties)?.let { established ->
                        sessionCache.putOutboundSession(
                            state.key,
                            SessionManager.SessionDirection.Outbound(state.state.toCounterparties(), established.session)
                        )
                        state.toResults(
                            established,
                        )
                    } ?: state.toResults(
                        CannotEstablishSession,
                    )
                }
            }
        }
    }

    override fun <T> getSessionsById(
        uuids: Collection<T>,
        getSessionId: (T) -> String,
    ): Collection<Pair<T, SessionManager.SessionDirection>> {
        if (uuids.isEmpty()) {
            return emptyList()
        }
        val traceable = uuids.groupBy { getSessionId(it) }
        val allCached = traceable.mapNotNull { (key, traces) ->
            sessionCache.getBySessionIfCached(key)?.let { sessionDirection ->
                key to (traces to sessionDirection)
            }
        }.toMap()
        val sessionIdsNotInCache = (traceable - allCached.keys)
        val inboundSessionsFromStateManager = if (sessionIdsNotInCache.isEmpty()) {
            emptyList()
        } else {
            sessionCache.validateStatesAndScheduleExpiry(
                stateManager.get(sessionIdsNotInCache.keys),
            )
                .entries
                .mapNotNull { (sessionId, state) ->
                    val session =
                        stateConvertor.toCordaSessionState(
                            state,
                            sessionManagerImpl.revocationCheckerClient::checkRevocation,
                        )?.sessionData as? Session
                    if (session == null) {
                        val counterparties = state.toCounterparties()
                        sendSessionReEstablishmentMessage(counterparties.ourId, counterparties.counterpartyId, sessionId)
                        return@mapNotNull null
                    }
                    sessionIdsNotInCache[sessionId]?.let { traceables ->
                        val inboundSession =
                            SessionManager.SessionDirection.Inbound(
                                state.toCounterparties(),
                                session,
                            )
                        sessionCache.putInboundSession(inboundSession)
                        traceables to inboundSession
                    }
                }
        }
        val sessionsNotInInboundStateManager =
            (sessionIdsNotInCache.keys - inboundSessionsFromStateManager.map { it.second.session.sessionId }.toSet()).map {
                getSessionIdFilter(it)
            }
        val outboundSessionsFromStateManager = if (sessionsNotInInboundStateManager.isEmpty()) {
            emptyList()
        } else {
            sessionCache.validateStatesAndScheduleExpiry(
                stateManager.findByMetadataMatchingAny(sessionsNotInInboundStateManager),
            )
                .entries
                .mapNotNull { (key, state) ->
                    val session =
                        stateConvertor.toCordaSessionState(
                            state,
                            sessionManagerImpl.revocationCheckerClient::checkRevocation,
                        )?.sessionData as? Session ?: return@mapNotNull null
                    val sessionId = state.metadata.toOutbound().sessionId
                    sessionIdsNotInCache[sessionId]?.let {
                        val outboundSession =
                            SessionManager.SessionDirection.Outbound(
                                state.toCounterparties(),
                                session,
                            )
                        sessionCache.putOutboundSession(key, outboundSession)
                        it to outboundSession
                    }
                }
        }

        return (allCached.values + inboundSessionsFromStateManager + outboundSessionsFromStateManager).flatMap {  (traceables, direction) ->
            traceables.map {
                it to direction
            }
        }
    }

    override fun <T> processSessionMessages(
        wrappedMessages: Collection<T>,
        getMessage: (T) -> LinkInMessage,
    ): Collection<Pair<T, LinkOutMessage?>> {
        val messages = wrappedMessages.map { it to getMessage(it) }
        val results = processInboundSessionMessages(messages) + processOutboundSessionMessages(messages)

        val failedUpdate =
            upsert(results.mapNotNull { it.result?.stateAction }).keys

        return results.mapNotNull { result ->
            if (failedUpdate.contains(result.result?.stateAction?.state?.key)) {
                null
            } else {
                result
            }
        }.onEach { result ->
            when (result.result?.message?.payload) {
                is AvroResponderHelloMessage, is AvroResponderHandshakeMessage -> {
                    result.result.sessionToCache?.let { sessionToCache ->
                        val session = SessionManager.SessionDirection.Inbound(
                            result.result.stateAction.state.toCounterparties(),
                            sessionToCache,
                        )
                        sessionCache.putInboundSession(session)
                        sessionEventPublisher.sessionCreated(sessionToCache.sessionId, SessionDirection.INBOUND)
                    }
                }
                is AvroInitiatorHelloMessage, is AvroInitiatorHandshakeMessage, null -> {
                    result.result?.sessionToCache?.let { sessionToCache ->
                        val key = result.result.stateAction.state.key
                        val outboundSession = SessionManager.SessionDirection.Outbound(
                            result.result.stateAction.state.toCounterparties(),
                            sessionToCache
                        )
                        sessionCache.putOutboundSession(key, outboundSession)
                        sessionEventPublisher.sessionCreated(key, SessionDirection.OUTBOUND)
                    }
                }
            }
        }.map { result ->
            result.traceable to result.result?.message
        }
    }

    override fun messageAcknowledged(sessionId: String) {
        // To be implemented in CORE-18730
        return
    }

    override fun inboundSessionEstablished(sessionId: String) {
        // Not needed by the Stateful Session Manager
        return
    }

    override fun dataMessageReceived(
        sessionId: String,
        source: HoldingIdentity,
        destination: HoldingIdentity,
    ) {
        // Not needed by the Stateful Session Manager
        return
    }

    override fun dataMessageSent(session: Session) {
        // Not needed by the Stateful Session Manager
        return
    }

    override fun deleteOutboundSession(
        counterParties: SessionManager.Counterparties, message: AuthenticatedMessage
    ) {
        val sessionId = try {
            schemaRegistry.deserialize(
                message.payload, ReEstablishSessionMessage::class.java, null
            ).sessionId
        } catch (e: Exception) {
            logger.warn("Could not deserialize '{}'. Outbound session will not be deleted.", ReEstablishSessionMessage::class.simpleName)
            return
        }

        val key = sessionCache.getKeyForOutboundSessionId(sessionId) ?: getCounterpartySerial(
            counterParties.ourId, counterParties.counterpartyId, message.header.statusFilter
        )?.let { serial ->
            calculateOutboundSessionKey(counterParties.ourId, counterParties.counterpartyId, serial)
        }
        if (key == null) {
            logger.warn("Could not delete outbound session '{}' lost by counterparty.", sessionId)
            return
        }
        stateManager.get(listOf(key)).values.firstOrNull()?.let {
            sessionCache.invalidateAndRemoveFromSchedular(it.key)
        }
    }

    private data class InboundSessionMessageContext<T>(
        val sessionId: String,
        val inboundSessionMessage: InboundSessionMessage,
        val trace: T,
    )

    private data class OutboundSessionMessageContext<T>(
        val sessionId: String,
        val outboundSessionMessage: OutboundSessionMessage,
        val trace: T,
    )

    private data class OutboundMessageContext<T>(
        val trace: T,
        val message: AuthenticatedMessageAndKey,
    )

    private data class OutboundMessageState<T>(
        val key: String?,
        val state: State?,
        val messages: Collection<OutboundMessageContext<T>>,
    ) {
        val first by lazy {
            messages.first()
        }
        val others by lazy {
            messages.drop(1)
        }

        fun toResults(
            sessionState: SessionManager.SessionState,
        ): Collection<OutboundMessageResults<T>> {
            return listOf(
                OutboundMessageResults(
                    key = this.key,
                    messages = this.messages,
                    action = null,
                    sessionState = sessionState,
                ),
            )
        }
        fun toResultsFirstAndOther(
            firstState: SessionManager.SessionState,
            otherStates: SessionManager.SessionState,
            action: StateManagerAction,
        ): Collection<OutboundMessageResults<T>> {
            return listOf(
                OutboundMessageResults(
                    key = this.key,
                    messages = listOf(first),
                    action = action,
                    sessionState = firstState,
                ),
                OutboundMessageResults(
                    key = this.key,
                    messages = others,
                    action = null,
                    sessionState = otherStates,
                ),
            )
        }
    }

    private data class OutboundMessageResults<T>(
        val key: String?,
        val messages: Collection<OutboundMessageContext<T>>,
        val action: StateManagerAction?,
        val sessionState: SessionManager.SessionState,
    )

    private fun <T> OutboundMessageContext<T>.sessionCounterparties() =
        sessionManagerImpl.getSessionCounterpartiesFromMessage(message.message)

    private fun calculateOutboundSessionKey(
        source: HoldingIdentity,
        destination: HoldingIdentity,
        serial: Long,
    ) = SessionCounterpartiesKey(source, destination, serial).hash

    private fun getCounterpartySerial(
        source: HoldingIdentity,
        destination: HoldingIdentity,
        status: MembershipStatusFilter,
    ): Long? = membershipGroupReaderProvider.lookup(source, destination, status)?.serial

    private data class SessionCounterpartiesKey(
        override val ourId: HoldingIdentity,
        override val counterpartyId: HoldingIdentity,
        val serial: Long,
    ) : SessionManager.BaseCounterparties {
        val hash: String by lazy(LazyThreadSafetyMode.PUBLICATION) {
            val s = (ourId.x500Name.toString() + counterpartyId.x500Name.toString() + serial.toString())
            val digest: MessageDigest = MessageDigest.getInstance(DigestAlgorithmName.SHA2_256.name)
            val hash: ByteArray = digest.digest(s.toByteArray())
            String(Base64.getEncoder().encode(SecureHashImpl(DigestAlgorithmName.SHA2_256.name, hash).bytes))
        }
    }

    private sealed interface InboundSessionMessage {
        data class InitiatorHelloMessage(
            val initiatorHelloMessage: AvroInitiatorHelloMessage,
        ) : InboundSessionMessage

        data class InitiatorHandshakeMessage(
            val initiatorHandshakeMessage: AvroInitiatorHandshakeMessage,
        ) : InboundSessionMessage
    }

    private sealed interface OutboundSessionMessage {
        data class ResponderHelloMessage(
            val responderHelloMessage: AvroResponderHelloMessage,
        ) : OutboundSessionMessage

        data class ResponderHandshakeMessage(
            val responderHandshakeMessage: AvroResponderHandshakeMessage,
        ) : OutboundSessionMessage
    }

    private data class TraceableResult<T>(
        val traceable: T,
        val result: Result?,
    )

    private data class Result(
        val message: LinkOutMessage?,
        val stateAction: StateManagerAction,
        val sessionToCache: Session?,
    )

    private fun <T> getCachedOutboundSessions(
        messagesAndKeys: Map<String?, Collection<OutboundMessageContext<T>>>,
    ): Map<String, Collection<Pair<T, SessionEstablished>>> {
        val allCached = sessionCache.getAllPresentOutboundSessions(messagesAndKeys.keys.filterNotNull())
        return allCached.mapValues { entry ->
            val contexts = messagesAndKeys[entry.key]
            val counterparties = contexts?.firstOrNull()?.let {
                sessionManagerImpl.getSessionCounterpartiesFromMessage(it.message.message)
            } ?: return@mapValues emptyList()

            contexts.map { context ->
                context.trace to SessionEstablished(entry.value.session, counterparties)
            }
        }.toMap()
    }

    private fun State.toCounterparties(): SessionManager.Counterparties {
        val common = this.metadata.toCommonMetadata()
        return SessionManager.Counterparties(
            ourId = common.source,
            counterpartyId = common.destination,
        )
    }

    private fun State.retrieveEstablishedSession(
        counterParties: SessionManager.SessionCounterparties,
    ): SessionEstablished? {
        val sessionData =
            stateConvertor.toCordaSessionState(
                this,
                sessionManagerImpl.revocationCheckerClient::checkRevocation,
            )?.sessionData
        return when (sessionData) {
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
            )
        val newState =
            State(
                calculateOutboundSessionKey(
                    counterParties.ourId,
                    counterParties.counterpartyId,
                    counterParties.serial,
                ),
                stateConvertor.toStateByteArray(SessionState(message.second, initMessage.first)),
                version = 0,
                metadata = newMetadata.toMetadata(),
            )
        return NewSessionsNeeded(listOf(message), counterParties) to newState
    }

    private fun State.replaySessionMessage(): Pair<NewSessionsNeeded, State>? {
        val sessionMessage =
            stateConvertor.toCordaSessionState(
                this,
                sessionManagerImpl.revocationCheckerClient::checkRevocation,
            )?.message ?: return null
        val outboundMetadata = metadata.toOutbound()
        val updatedMetadata = outboundMetadata.copy(
            commonData = outboundMetadata.commonData.copy(
                lastSendTimestamp = clock.instant(),
            ),
        )
        val updatedState =
            State(
                key,
                value,
                version = version,
                metadata = updatedMetadata.toMetadata(),
            )
        return NewSessionsNeeded(
            listOf(updatedMetadata.sessionId to sessionMessage),
            updatedState.getSessionCounterparties(),
        ) to updatedState
    }
    private fun <T> processStateUpdates(
        resultStates: Collection<OutboundMessageResults<T>>,
    ): Collection<Pair<T, SessionManager.SessionState>> {
        val updates = resultStates.mapNotNull { it.action }
        val failedUpdates = upsert(updates)

        return resultStates.flatMap { resultState ->
            val key = resultState.key
            if (failedUpdates.containsKey(key)) {
                val savedState = failedUpdates[key]
                val savedMetadata = savedState?.metadata?.toOutbound()
                val newState = when (savedMetadata?.status) {
                    OutboundSessionStatus.SentInitiatorHello, OutboundSessionStatus.SentInitiatorHandshake ->
                        resultState.messages.first().sessionCounterparties()?.let {
                            SessionAlreadyPending(it)
                        } ?: CannotEstablishSession
                    OutboundSessionStatus.SessionReady -> resultState.messages.first().sessionCounterparties()?.let {
                        savedState.retrieveEstablishedSession(it)
                    } ?: CannotEstablishSession
                    null -> CannotEstablishSession
                }
                resultState.messages.map { it.trace to newState }
            } else {
                resultState.messages.map { it.trace to resultState.sessionState }
            }
        }
    }
    private fun <T> processInboundSessionMessages(messages: List<Pair<T, LinkInMessage?>>): Collection<TraceableResult<T>> {
        val messageContexts =
            messages.mapNotNull {
                it.second?.payload?.getSessionIdIfInboundSessionMessage(it.first)
            }.groupBy {
                it.sessionId
            }
        if (messageContexts.isEmpty()) {
            return emptyList()
        }
        val states = sessionCache.validateStatesAndScheduleExpiry(
            stateManager.get(messageContexts.keys),
        )
        return messageContexts.flatMap { (sessionId, contexts) ->
            val state = states[sessionId]
            val result =
                when (val lastMessage = contexts.last().inboundSessionMessage) {
                    is InboundSessionMessage.InitiatorHelloMessage -> {
                        processInitiatorHello(state, lastMessage)?.let { (message, stateUpdate) ->
                            Result(message, CreateAction(stateUpdate), null)
                        }
                    }
                    is InboundSessionMessage.InitiatorHandshakeMessage -> {
                        processInitiatorHandshake(state, lastMessage)?.let { (message, stateUpdate, session) ->
                            Result(message, UpdateAction(stateUpdate), session)
                        }
                    }
                }
            contexts.map { TraceableResult(it.trace, result) }
        }
    }

    private fun <T> processOutboundSessionMessages(messages: List<Pair<T, LinkInMessage?>>): Collection<TraceableResult<T>> {
        val messageContexts =
            messages.mapNotNull {
                it.second?.payload?.getSessionIdIfOutboundSessionMessage(it.first)
            }.groupBy {
                it.sessionId
            }
        if (messageContexts.isEmpty()) {
            return emptyList()
        }
        val states =
            stateManager
                .findByMetadataMatchingAny(messageContexts.keys.map { getSessionIdFilter(it) })
                .values.associateBy { state ->
                    state.metadata.toOutbound().sessionId
                }
        return messageContexts.flatMap { (sessionId, contexts) ->
            val state = states[sessionId]
            val result =
                when (val lastMessage = contexts.last().outboundSessionMessage) {
                    is OutboundSessionMessage.ResponderHelloMessage -> {
                        processResponderHello(state, lastMessage)?.let { (message, stateUpdate) ->
                            Result(message, UpdateAction(stateUpdate), null)
                        }
                    }
                    is OutboundSessionMessage.ResponderHandshakeMessage -> {
                        processResponderHandshake(state, lastMessage)?.let { (message, stateUpdate, session) ->
                            Result(message, UpdateAction(stateUpdate), session)
                        }
                    }
                }
            contexts.map { TraceableResult(it.trace, result) }
        }
    }

    private fun getSessionIdFilter(sessionId: String): MetadataFilter = MetadataFilter("sessionId", Operation.Equals, sessionId)

    /**
     * TODO Refactor SessionManagerImpl to move logic needed here i.e. create an ResponderHello from an InitiatorHello
     * into a new component. This component should not store the AuthenticationProtocol in an in memory map or replay session
     * messages.
     */
    private fun processInitiatorHello(
        state: State?,
        message: InboundSessionMessage.InitiatorHelloMessage,
    ): Pair<LinkOutMessage?, State>? {
        val metadata = state?.metadata?.toInbound()
        return when (metadata?.status) {
            null -> {
                sessionManagerImpl.processInitiatorHello(message.initiatorHelloMessage)?.let {
                        (responseMessage, authenticationProtocol) ->
                    val timestamp = clock.instant()
                    val newMetadata =
                        InboundSessionMetadata(
                            CommonMetadata(
                                destination = responseMessage.header.destinationIdentity.toCorda(),
                                source = responseMessage.header.sourceIdentity.toCorda(),
                                lastSendTimestamp = timestamp,
                                expiry = timestamp + SESSION_VALIDITY_PERIOD,
                            ),
                            status = InboundSessionStatus.SentResponderHello,
                        )
                    val newState =
                        State(
                            message.initiatorHelloMessage.header.sessionId,
                            stateConvertor.toStateByteArray(SessionState(responseMessage, authenticationProtocol)),
                            version = 0,
                            metadata = newMetadata.toMetadata(),
                        )
                    responseMessage to newState
                }
            }
            InboundSessionStatus.SentResponderHello -> {
                if (metadata.lastSendExpired(clock)) {
                    val timestamp = clock.instant()
                    val updatedMetadata = metadata.copy(
                        commonData = metadata.commonData.copy(
                            lastSendTimestamp = timestamp,
                        ),
                    )
                    val responderHelloToResend =
                        stateConvertor.toCordaSessionState(
                            state,
                            sessionManagerImpl.revocationCheckerClient::checkRevocation,
                        )?.message ?: return null
                    val newState =
                        State(
                            key = state.key,
                            value = state.value,
                            version = state.version,
                            metadata = updatedMetadata.toMetadata(),
                        )
                    responderHelloToResend to newState
                } else {
                    null
                }
            }
            InboundSessionStatus.SentResponderHandshake -> {
                null
            }
        }
    }

    private fun processResponderHello(
        state: State?,
        message: OutboundSessionMessage.ResponderHelloMessage,
    ): Pair<LinkOutMessage?, State>? {
        val metadata = state?.metadata?.toOutbound()
        return when (metadata?.status) {
            OutboundSessionStatus.SentInitiatorHello -> {
                val sessionState =
                    stateConvertor.toCordaSessionState(
                        state,
                        sessionManagerImpl.revocationCheckerClient::checkRevocation,
                    )?.sessionData as? AuthenticationProtocolInitiator ?: return null
                val counterparties = state.getSessionCounterparties()

                sessionManagerImpl.processResponderHello(
                    counterparties,
                    sessionState,
                    message.responderHelloMessage,
                )?.let { (responseMessage, authenticationProtocol) ->
                    val timestamp = clock.instant()
                    val updatedMetadata =
                        metadata.copy(
                            commonData = metadata.commonData.copy(
                                lastSendTimestamp = timestamp,
                                expiry = timestamp + SESSION_VALIDITY_PERIOD,
                            ),
                            sessionId = message.responderHelloMessage.header.sessionId,
                            status = OutboundSessionStatus.SentInitiatorHandshake,
                        )
                    val newState =
                        State(
                            calculateOutboundSessionKey(
                                counterparties.ourId,
                                counterparties.counterpartyId,
                                counterparties.serial,
                            ),
                            stateConvertor.toStateByteArray(SessionState(responseMessage, authenticationProtocol)),
                            version = state.version,
                            metadata = updatedMetadata.toMetadata(),
                        )
                    responseMessage to newState
                }
            }

            OutboundSessionStatus.SentInitiatorHandshake -> {
                if (metadata.lastSendExpired(clock)) {
                    val updatedMetadata = metadata.copy(
                        commonData = metadata.commonData.copy(
                            lastSendTimestamp = clock.instant(),
                        ),
                    )
                    val initiatorHandshakeToResend =
                        stateConvertor.toCordaSessionState(
                            state,
                            sessionManagerImpl.revocationCheckerClient::checkRevocation,
                        )?.message ?: return null
                    val newState =
                        State(
                            key = state.key,
                            value = state.value,
                            version = state.version,
                            metadata = updatedMetadata.toMetadata(),
                        )
                    initiatorHandshakeToResend to newState
                } else {
                    null
                }
            }

            OutboundSessionStatus.SessionReady -> {
                logger.alreadySessionWarning(
                    message::class.java.simpleName,
                    message.responderHelloMessage.header.sessionId,
                )
                null
            }

            null -> {
                logger.noSessionWarning(message::class.java.simpleName, message.responderHelloMessage.header.sessionId)
                null
            }
        }
    }

    private data class ProcessHandshakeResult(
        val responseMessage: LinkOutMessage?,
        val stateToUpdate: State,
        val session: Session?,
    )

    private fun processInitiatorHandshake(
        state: State?,
        message: InboundSessionMessage.InitiatorHandshakeMessage,
    ): ProcessHandshakeResult? {
        val metadata = state?.metadata?.toInbound()
        return when (metadata?.status) {
            null -> {
                null
            }
            InboundSessionStatus.SentResponderHello -> {
                val sessionData =
                    stateConvertor.toCordaSessionState(
                        state,
                        sessionManagerImpl.revocationCheckerClient::checkRevocation,
                    )?.sessionData as? AuthenticationProtocolResponder
                if (sessionData == null) {
                    logger.warn(
                        "Session ${state.key} has status SentResponderHello by the saved data is" +
                            " not AuthenticationProtocolResponder.",
                    )
                    return null
                }
                sessionManagerImpl.processInitiatorHandshake(sessionData, message.initiatorHandshakeMessage)?.let { responseMessage ->
                    val timestamp = clock.instant()
                    val newMetadata =
                        InboundSessionMetadata(
                            commonData = CommonMetadata(
                                source = responseMessage.header.sourceIdentity.toCorda(),
                                destination = responseMessage.header.destinationIdentity.toCorda(),
                                lastSendTimestamp = timestamp,
                                expiry = timestamp + SESSION_VALIDITY_PERIOD,
                            ),
                            status = InboundSessionStatus.SentResponderHandshake,
                        )
                    val session = sessionData.getSession()
                    val newState =
                        State(
                            message.initiatorHandshakeMessage.header.sessionId,
                            stateConvertor.toStateByteArray(SessionState(responseMessage, session)),
                            version = state.version,
                            metadata = newMetadata.toMetadata(),
                        )
                    ProcessHandshakeResult(responseMessage, newState, session)
                }
            }
            InboundSessionStatus.SentResponderHandshake -> {
                if (metadata.lastSendExpired(clock)) {
                    val timestamp = clock.instant()
                    val updatedMetadata = metadata.copy(
                        commonData = metadata.commonData.copy(
                            lastSendTimestamp = timestamp,
                        ),
                    )
                    val responderHandshakeToResend =
                        stateConvertor.toCordaSessionState(
                            state,
                            sessionManagerImpl.revocationCheckerClient::checkRevocation,
                        )?.message ?: return null
                    val newState =
                        State(
                            key = state.key,
                            value = state.value,
                            version = state.version,
                            metadata = updatedMetadata.toMetadata(),
                        )
                    ProcessHandshakeResult(responderHandshakeToResend, newState, null)
                } else {
                    null
                }
            }
        }
    }

    private fun processResponderHandshake(
        state: State?,
        message: OutboundSessionMessage.ResponderHandshakeMessage,
    ): ProcessHandshakeResult? {
        val metadata = state?.metadata?.toOutbound()
        return when (metadata?.status) {
            OutboundSessionStatus.SentInitiatorHandshake -> {
                val sessionState =
                    stateConvertor.toCordaSessionState(
                        state,
                        sessionManagerImpl.revocationCheckerClient::checkRevocation,
                    )?.sessionData as? AuthenticationProtocolInitiator ?: return null
                val counterparties = state.getSessionCounterparties()

                sessionManagerImpl.processResponderHandshake(
                    message.responderHandshakeMessage,
                    counterparties,
                    sessionState,
                )?.let { session ->
                    val updatedMetadata =
                        metadata.copy(
                            status = OutboundSessionStatus.SessionReady,
                            commonData = metadata.commonData.copy(
                                lastSendTimestamp = clock.instant(),
                            ),
                        )
                    val newState =
                        State(
                            calculateOutboundSessionKey(
                                counterparties.ourId,
                                counterparties.counterpartyId,
                                counterparties.serial,
                            ),
                            stateConvertor.toStateByteArray(SessionState(null, session)),
                            version = state.version,
                            metadata = updatedMetadata.toMetadata(),
                        )
                    ProcessHandshakeResult(null, newState, session)
                }
            }

            OutboundSessionStatus.SentInitiatorHello -> {
                logger.error(
                    "Received ${message::class.java.simpleName} with session ID ${metadata.sessionId} but the corresponding pending " +
                        "session with this ID has an unexpected status ${metadata.status}",
                )
                null
            }

            OutboundSessionStatus.SessionReady -> {
                logger.alreadySessionWarning(
                    message::class.java.simpleName,
                    message.responderHandshakeMessage.header.sessionId,
                )
                null
            }

            null -> {
                logger.noSessionWarning(
                    message::class.java.simpleName,
                    message.responderHandshakeMessage.header.sessionId,
                )
                null
            }
        }
    }

    private fun <T> Any.getSessionIdIfInboundSessionMessage(trace: T): InboundSessionMessageContext<T>? {
        return when (this) {
            is AvroInitiatorHelloMessage ->
                this.header?.sessionId?.let { sessionId ->
                    InboundSessionMessageContext(
                        sessionId,
                        InboundSessionMessage.InitiatorHelloMessage(this),
                        trace,
                    )
                }
            is AvroInitiatorHandshakeMessage ->
                this.header?.sessionId?.let { sessionId ->
                    InboundSessionMessageContext(
                        sessionId,
                        InboundSessionMessage.InitiatorHandshakeMessage(this),
                        trace,
                    )
                }
            else -> null
        }
    }

    private fun <T> Any.getSessionIdIfOutboundSessionMessage(trace: T): OutboundSessionMessageContext<T>? {
        return when (this) {
            is AvroResponderHelloMessage ->
                this.header?.sessionId?.let { sessionId ->
                    OutboundSessionMessageContext(
                        sessionId,
                        OutboundSessionMessage.ResponderHelloMessage(this),
                        trace,
                    )
                }

            is AvroResponderHandshakeMessage ->
                this.header?.sessionId?.let { sessionId ->
                    OutboundSessionMessageContext(
                        sessionId,
                        OutboundSessionMessage.ResponderHandshakeMessage(this),
                        trace,
                    )
                }
            else -> null
        }
    }

    private fun State.getSessionCounterparties(): SessionManager.SessionCounterparties {
        val metadata = this.metadata.toOutbound()
        return SessionManager.SessionCounterparties(
            metadata.commonData.source,
            metadata.commonData.destination,
            metadata.membershipStatus,
            metadata.serial,
            metadata.communicationWithMgm,
        )
    }
    private fun upsert(
        changes: Collection<StateManagerAction>,
    ): Map<String, State?> {
        val updates = changes.filterIsInstance<UpdateAction>()
            .map {
                it.state
            }.mapNotNull {
                sessionCache.validateStateAndScheduleExpiry(it)
            }
        val creates = changes.filterIsInstance<CreateAction>()
            .map {
                it.state
            }.mapNotNull {
                sessionCache.validateStateAndScheduleExpiry(it)
            }
        val failedUpdates = if (updates.isNotEmpty()) {
            stateManager.update(updates).onEach {
                logger.info("Failed to update the state of session with ID ${it.key}")
            }
        } else {
            emptyMap()
        }
        val failedCreates = if (creates.isNotEmpty()) {
            stateManager.create(creates).associateWith {
                logger.info("Failed to create the state of session with ID $it")
                null
            }
        } else {
            emptyMap()
        }
        return failedUpdates + failedCreates
    }

    private val sessionEventPublisher = StatefulSessionEventPublisher(
        coordinatorFactory,
        publisherFactory,
        messagingConfig,
    )
    private val sessionCache = SessionCache(
        stateManager,
        clock,
        sessionEventPublisher,
    )

    private fun sendSessionReEstablishmentMessage(
        source: HoldingIdentity,
        destination: HoldingIdentity,
        sessionId: String,
    ) {
        val messageBytes = schemaRegistry.serialize(
            ReEstablishSessionMessage(sessionId)
        ).array()
        val record = createAuthenticatedMessageRecord(source, destination, messageBytes)
        logger.info("Sending '{}' to session initiator '{}'.", ReEstablishSessionMessage::class.simpleName, destination)
        sessionManagerImpl.publisher.publish(listOf(record))
    }

    private fun createAuthenticatedMessageRecord(
        source: HoldingIdentity,
        destination: HoldingIdentity,
        payload: ByteArray,
    ): Record<String, AppMessage> {
        val header = AuthenticatedMessageHeader.newBuilder()
            .setDestination(destination.toAvro())
            .setSource(source.toAvro())
            .setMessageId(UUID.randomUUID().toString())
            .setSubsystem(LINK_MANAGER_SUBSYSTEM)
            .setStatusFilter(MembershipStatusFilter.ACTIVE)
            .setTtl(null)
            .setTraceId(null)
            .build()
        val message = AuthenticatedMessage.newBuilder()
            .setHeader(header)
            .setPayload(ByteBuffer.wrap(payload))
            .build()
        val appMessage = AppMessage(message)

        return Record(
            P2P_OUT_TOPIC,
            UUID.randomUUID().toString(),
            appMessage,
        )
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
                sessionEventPublisher.dominoTile.coordinatorName,
                sessionEventListener.dominoTile.coordinatorName
            ),
            managedChildren =
            setOf(
                sessionManagerImpl.dominoTile.toNamedLifecycle(),
                sessionEventPublisher.dominoTile.toNamedLifecycle(),
                sessionEventListener.dominoTile.toNamedLifecycle(),
            ),
        )
}
