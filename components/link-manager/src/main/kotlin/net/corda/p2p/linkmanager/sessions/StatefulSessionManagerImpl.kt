package net.corda.p2p.linkmanager.sessions

import net.corda.crypto.client.SessionEncryptionOpsClient
import net.corda.crypto.core.SecureHashImpl
import net.corda.crypto.core.bytes
import net.corda.data.p2p.AuthenticatedMessageAndKey
import net.corda.data.p2p.LinkInMessage
import net.corda.data.p2p.LinkOutMessage
import net.corda.data.p2p.ReEstablishSessionMessage
import net.corda.data.p2p.app.AuthenticatedMessage
import net.corda.data.p2p.app.MembershipStatusFilter
import net.corda.data.p2p.event.SessionCreated
import net.corda.data.p2p.event.SessionDirection
import net.corda.data.p2p.event.SessionEvent
import net.corda.libs.configuration.SmartConfig
import net.corda.libs.statemanager.api.MetadataFilter
import net.corda.libs.statemanager.api.Operation
import net.corda.libs.statemanager.api.State
import net.corda.libs.statemanager.api.StateManager
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.LifecycleCoordinatorName
import net.corda.lifecycle.domino.logic.ComplexDominoTile
import net.corda.membership.lib.MemberInfoExtension.Companion.isMgm
import net.corda.membership.read.MembershipGroupReaderProvider
import net.corda.messaging.api.records.Record
import net.corda.messaging.api.subscription.factory.SubscriptionFactory
import net.corda.p2p.crypto.protocol.api.AuthenticatedEncryptionSession
import net.corda.p2p.crypto.protocol.api.AuthenticatedSession
import net.corda.p2p.crypto.protocol.api.AuthenticationProtocolInitiator
import net.corda.p2p.crypto.protocol.api.AuthenticationProtocolResponder
import net.corda.p2p.crypto.protocol.api.Session
import net.corda.p2p.linkmanager.common.MessageConverter.Companion.createLinkOutMessage
import net.corda.p2p.linkmanager.membership.lookup
import net.corda.p2p.linkmanager.sessions.SessionManager.SessionState.CannotEstablishSession
import net.corda.p2p.linkmanager.sessions.SessionManager.SessionState.NewSessionsNeeded
import net.corda.p2p.linkmanager.sessions.SessionManager.SessionState.SessionAlreadyPending
import net.corda.p2p.linkmanager.sessions.SessionManager.SessionState.SessionEstablished
import net.corda.p2p.linkmanager.sessions.SessionManagerWarnings.alreadySessionWarning
import net.corda.p2p.linkmanager.sessions.SessionManagerWarnings.couldNotFindSessionInformation
import net.corda.p2p.linkmanager.sessions.SessionManagerWarnings.noSessionWarning
import net.corda.p2p.linkmanager.sessions.events.StatefulSessionEventProcessor
import net.corda.p2p.linkmanager.sessions.events.StatefulSessionEventPublisher
import net.corda.p2p.linkmanager.sessions.metadata.CommonMetadata
import net.corda.p2p.linkmanager.sessions.metadata.InboundSessionMetadata
import net.corda.p2p.linkmanager.sessions.metadata.InboundSessionMetadata.Companion.toInbound
import net.corda.p2p.linkmanager.sessions.metadata.InboundSessionStatus
import net.corda.p2p.linkmanager.sessions.metadata.OutboundSessionMetadata
import net.corda.p2p.linkmanager.sessions.metadata.OutboundSessionMetadata.Companion.toOutbound
import net.corda.p2p.linkmanager.sessions.metadata.OutboundSessionStatus
import net.corda.p2p.linkmanager.sessions.metadata.toCounterparties
import net.corda.p2p.linkmanager.state.SessionState
import net.corda.p2p.messaging.P2pRecordsFactory
import net.corda.schema.Schemas
import net.corda.schema.registry.AvroSchemaRegistry
import net.corda.utilities.time.Clock
import net.corda.v5.crypto.DigestAlgorithmName
import net.corda.v5.membership.MemberInfo
import net.corda.virtualnode.HoldingIdentity
import net.corda.virtualnode.toCorda
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.security.MessageDigest
import java.time.Duration
import java.util.Base64
import net.corda.data.p2p.crypto.InitiatorHandshakeMessage as AvroInitiatorHandshakeMessage
import net.corda.data.p2p.crypto.InitiatorHelloMessage as AvroInitiatorHelloMessage
import net.corda.data.p2p.crypto.ResponderHandshakeMessage as AvroResponderHandshakeMessage
import net.corda.data.p2p.crypto.ResponderHelloMessage as AvroResponderHelloMessage

@Suppress("TooManyFunctions", "LongParameterList", "LargeClass")
internal class StatefulSessionManagerImpl(
    subscriptionFactory: SubscriptionFactory,
    messagingConfig: SmartConfig,
    coordinatorFactory: LifecycleCoordinatorFactory,
    stateManager: StateManager,
    private val sessionManagerImpl: SessionManagerImpl,
    private val stateConvertor: StateConvertor,
    private val clock: Clock,
    private val membershipGroupReaderProvider: MembershipGroupReaderProvider,
    private val deadSessionMonitor: DeadSessionMonitor,
    private val schemaRegistry: AvroSchemaRegistry,
    private val sessionCache: SessionCache,
    sessionEventPublisher: StatefulSessionEventPublisher,
    p2pRecordsFactory: P2pRecordsFactory,
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
                stateManager.get(notInCache.keys.filterNotNull())
                    .let { states ->
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
        val metadata = state.stateManagerSessionState?.managerState?.metadata?.toOutbound()
        return if (metadata?.lastSendExpired(clock) == false) {
            when (metadata.status) {
                OutboundSessionStatus.SentInitiatorHello, OutboundSessionStatus.SentInitiatorHandshake -> {
                    state.toResults(
                        SessionAlreadyPending(counterparties),
                    )
                }

                OutboundSessionStatus.SessionReady -> {
                    state.stateManagerSessionState.sessionState.retrieveEstablishedSession(counterparties)?.let { establishedState ->
                        sessionCache.putOutboundSession(
                            state.key,
                            SessionManager.SessionDirection.Outbound(
                                state.stateManagerSessionState.toCounterparties(),
                                establishedState.session,
                            ),
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
                        CannotEstablishSession,
                    )
                }

                OutboundSessionStatus.SessionReady -> {
                    state.stateManagerSessionState.sessionState.retrieveEstablishedSession(counterparties)?.let { established ->
                        sessionCache.putOutboundSession(
                            state.key,
                            SessionManager.SessionDirection.Outbound(
                                state.stateManagerSessionState.toCounterparties(),
                                established.session,
                            ),
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
            stateManager.get(sessionIdsNotInCache.keys)
                .entries
                .mapNotNull { (sessionId, state) ->
                    val session = state.sessionState.sessionData as? Session ?: return@mapNotNull null
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
            (sessionIdsNotInCache.keys - inboundSessionsFromStateManager.map { it.second.session.sessionId }.toSet())
        val outboundSessionsFromStateManager = if (sessionsNotInInboundStateManager.isEmpty()) {
            emptyList()
        } else {
            stateManager.findStatesMatchingAny(
                sessionsNotInInboundStateManager.map { getSessionIdFilter(it) },
            )
                .entries
                .mapNotNull { (key, state) ->
                    val session = state.sessionState.sessionData as? Session ?: return@mapNotNull null
                    val sessionId = state.managerState.metadata.toOutbound().sessionId
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
        val sessionsNotFound =
            (
                sessionsNotInInboundStateManager - outboundSessionsFromStateManager.map { it.second.session.sessionId }
                    .toSet()
                ).mapNotNull { sessionId ->
                sessionIdsNotInCache[sessionId]?.let {
                    it to SessionManager.SessionDirection.NoSession
                }
            }

        return (allCached.values + inboundSessionsFromStateManager + outboundSessionsFromStateManager + sessionsNotFound)
            .flatMap { (traceables, direction) ->
                traceables.map {
                    it to direction
                }
            }
    }

    override fun <T> processSessionMessages(
        wrappedMessages: Collection<T>,
        getMessage: (T) -> LinkInMessage,
    ): Collection<Pair<T, SessionManager.ProcessSessionMessagesResult>> {
        val messages = wrappedMessages.map { it to getMessage(it) }
        val results = processInboundSessionMessages(messages) + processOutboundSessionMessages(messages)

        val failedUpdate =
            stateManager.upsert(results.mapNotNull { it.result?.stateAction }).keys

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
                        val session = SessionManager.SessionDirection.Inbound(
                            result.result.stateAction.state.toCounterparties(),
                            sessionToCache,
                        )
                        sessionCache.putInboundSession(session)
                        recordsForSessionCreated(sessionToCache.sessionId, SessionDirection.INBOUND)
                    } ?: emptyList()
                }
                is AvroInitiatorHelloMessage, is AvroInitiatorHandshakeMessage, null -> {
                    result.result?.sessionToCache?.let { sessionToCache ->
                        val key = result.result.stateAction.state.key
                        val outboundSession = SessionManager.SessionDirection.Outbound(
                            result.result.stateAction.state.toCounterparties(),
                            sessionToCache,
                        )
                        sessionCache.putOutboundSession(key, outboundSession)
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

        val key = sessionCache.getKeyForOutboundSessionId(sessionId) ?: getCounterpartySerial(
            counterParties.ourId,
            counterParties.counterpartyId,
            message.header.statusFilter,
        )?.let { serial ->
            calculateOutboundSessionKey(counterParties.ourId, counterParties.counterpartyId, serial)
        }

        deadSessionMonitor.sessionRemoved(sessionId)

        if (key == null) {
            logger.warn("Could not delete outbound session '{}' lost by counterparty.", sessionId)
            return
        }

        sessionCache.deleteByKey(key)
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
        val stateManagerSessionState: StateManagerWrapper.StateManagerSessionState?,
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
            val firstResult = OutboundMessageResults(
                key = this.key,
                messages = listOf(first),
                action = action,
                sessionState = firstState,
            )
            return if (others.isEmpty()) {
                listOf(firstResult)
            } else {
                listOf(
                    firstResult,
                    OutboundMessageResults(
                        key = this.key,
                        messages = others,
                        action = null,
                        sessionState = otherStates,
                    ),
                )
            }
        }
    }

    private data class OutboundMessageResults<T>(
        val key: String?,
        val messages: Collection<OutboundMessageContext<T>>,
        val action: StateManagerAction?,
        val sessionState: SessionManager.SessionState,
    )
    private fun AuthenticatedMessage.getSessionCounterpartiesFromMessage(): SessionManager.SessionCounterparties? {
        val peer = this.header.destination
        val us = this.header.source
        val status = this.header.statusFilter
        val ourInfo = membershipGroupReaderProvider.lookup(
            us.toCorda(), us.toCorda(), MembershipStatusFilter.ACTIVE_OR_SUSPENDED
        )
        // could happen when member has pending registration or something went wrong
        if (ourInfo == null) {
            logger.warn("Could not get member information about us from message sent from $us" +
                    " to $peer with ID `${this.header.messageId}`.")
        }
        val counterpartyInfo = membershipGroupReaderProvider.lookup(us.toCorda(), peer.toCorda(), status)
        if (counterpartyInfo == null) {
            logger.couldNotFindSessionInformation(us.toCorda().shortHash, peer.toCorda().shortHash, this.header.messageId)
            return null
        }
        return SessionManager.SessionCounterparties(
            us.toCorda(),
            peer.toCorda(),
            status,
            counterpartyInfo.serial,
            isCommunicationBetweenMgmAndMember(ourInfo, counterpartyInfo)
        )
    }
    private fun isCommunicationBetweenMgmAndMember(ourInfo: MemberInfo?, counterpartyInfo: MemberInfo): Boolean {
        return counterpartyInfo.isMgm || ourInfo?.isMgm == true
    }

    private fun <T> OutboundMessageContext<T>.sessionCounterparties() =
        message.message.getSessionCounterpartiesFromMessage()

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
            val counterparties = contexts?.firstOrNull()
                ?.message
                ?.message
                ?.getSessionCounterpartiesFromMessage() ?: return@mapValues emptyList()

            contexts.map { context ->
                context.trace to SessionEstablished(entry.value.session, counterparties)
            }
        }.toMap()
    }

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
        val updatedState =
            State(
                this.managerState.key,
                this.managerState.value,
                version = this.managerState.version,
                metadata = updatedMetadata.toMetadata(),
            )
        return NewSessionsNeeded(
            listOf(updatedMetadata.sessionId to linkOutMessage),
            updatedState.getSessionCounterparties(),
        ) to updatedState
    }

    private fun <T> processStateUpdates(
        resultStates: Collection<OutboundMessageResults<T>>,
    ): Collection<Pair<T, SessionManager.SessionState>> {
        val updates = resultStates.mapNotNull { it.action }
        val failedUpdates = stateManager.upsert(updates)

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
                        val session = stateConvertor.toCordaSessionState(
                            savedState,
                            sessionManagerImpl.revocationCheckerClient::checkRevocation,
                        )
                        session?.retrieveEstablishedSession(it)
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
        val states = stateManager.get(messageContexts.keys)

        return messageContexts.flatMap { (sessionId, contexts) ->
            val state = states[sessionId]
            val lastContext = contexts.last()
            val otherContexts = contexts.dropLast(1)
            val result =
                when (val lastMessage = lastContext.inboundSessionMessage) {
                    is InboundSessionMessage.InitiatorHelloMessage -> {
                        processInitiatorHello(state, lastMessage)
                    }
                    is InboundSessionMessage.InitiatorHandshakeMessage -> {
                        processInitiatorHandshake(state, lastMessage)
                    }
                }
            otherContexts.map {
                TraceableResult(it.trace, null)
            } + TraceableResult(
                lastContext.trace,
                result,
            )
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
                .findStatesMatchingAny(messageContexts.keys.map { getSessionIdFilter(it) })
                .values.associateBy { state ->
                    state.managerState.metadata.toOutbound().sessionId
                }
        return messageContexts.flatMap { (sessionId, contexts) ->
            val state = states[sessionId]
            val lastContext = contexts.last()
            val otherContexts = contexts.dropLast(1)
            val result =
                when (val lastMessage = lastContext.outboundSessionMessage) {
                    is OutboundSessionMessage.ResponderHelloMessage -> {
                        processResponderHello(state, lastMessage)
                    }
                    is OutboundSessionMessage.ResponderHandshakeMessage -> {
                        processResponderHandshake(state, lastMessage)
                    }
                }
            otherContexts.map {
                TraceableResult(it.trace, null)
            } +
                    TraceableResult(lastContext.trace, result)
        }
    }

    private fun getSessionIdFilter(sessionId: String): MetadataFilter = MetadataFilter("sessionId", Operation.Equals, sessionId)

    /**
     * TODO Refactor SessionManagerImpl to move logic needed here i.e. create an ResponderHello from an InitiatorHello
     * into a new component. This component should not store the AuthenticationProtocol in an in memory map or replay session
     * messages.
     */
    private fun processInitiatorHello(
        state: StateManagerWrapper.StateManagerSessionState?,
        message: InboundSessionMessage.InitiatorHelloMessage,
    ): Result? {
        val metadata = state?.managerState?.metadata?.toInbound()
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
                    Result(responseMessage, CreateAction(newState), null)
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
                    val sessionState = state.sessionState
                    val responderHelloToResend = sessionState.message
                    val newState =
                        State(
                            key = state.managerState.key,
                            value = state.managerState.value,
                            version = state.managerState.version,
                            metadata = updatedMetadata.toMetadata(),
                        )
                    Result(responderHelloToResend, UpdateAction(newState, true), null)
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
        state: StateManagerWrapper.StateManagerSessionState?,
        message: OutboundSessionMessage.ResponderHelloMessage,
    ): Result? {
        val metadata = state?.managerState?.metadata?.toOutbound()
        return when (metadata?.status) {
            OutboundSessionStatus.SentInitiatorHello -> {
                val sessionState = state.sessionState.sessionData as? AuthenticationProtocolInitiator ?: return null
                val counterparties = state.managerState.getSessionCounterparties()

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
                            version = state.managerState.version,
                            metadata = updatedMetadata.toMetadata(),
                        )
                    Result(responseMessage, UpdateAction(newState, false), null)
                }
            }

            OutboundSessionStatus.SentInitiatorHandshake -> {
                if (metadata.lastSendExpired(clock)) {
                    val updatedMetadata = metadata.copy(
                        commonData = metadata.commonData.copy(
                            lastSendTimestamp = clock.instant(),
                        ),
                    )
                    val initiatorHandshakeToResend = state.sessionState.message ?: return null
                    val newState =
                        State(
                            key = state.managerState.key,
                            value = state.managerState.value,
                            version = state.managerState.version,
                            metadata = updatedMetadata.toMetadata(),
                        )
                    Result(initiatorHandshakeToResend, UpdateAction(newState, true), null)
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

    private fun processInitiatorHandshake(
        state: StateManagerWrapper.StateManagerSessionState?,
        message: InboundSessionMessage.InitiatorHandshakeMessage,
    ): Result? {
        val metadata = state?.managerState?.metadata?.toInbound()
        return when (metadata?.status) {
            null -> {
                null
            }
            InboundSessionStatus.SentResponderHello -> {
                val sessionData = state.sessionState.sessionData as? AuthenticationProtocolResponder
                if (sessionData == null) {
                    logger.warn(
                        "Session ${state.managerState.key} has status SentResponderHello by the saved data is" +
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
                            version = state.managerState.version,
                            metadata = newMetadata.toMetadata(),
                        )
                    Result(responseMessage, UpdateAction(newState, false), session)
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
                    val responderHandshakeToResend = state.sessionState.message
                    val newState =
                        State(
                            key = state.managerState.key,
                            value = state.managerState.value,
                            version = state.managerState.version,
                            metadata = updatedMetadata.toMetadata(),
                        )
                    Result(responderHandshakeToResend, UpdateAction(newState, true), null)
                } else {
                    null
                }
            }
        }
    }

    private fun processResponderHandshake(
        state: StateManagerWrapper.StateManagerSessionState?,
        message: OutboundSessionMessage.ResponderHandshakeMessage,
    ): Result? {
        val metadata = state?.managerState?.metadata?.toOutbound()
        return when (metadata?.status) {
            OutboundSessionStatus.SentInitiatorHandshake -> {
                val sessionState = state.sessionState.sessionData as? AuthenticationProtocolInitiator ?: return null
                val counterparties = state.managerState.getSessionCounterparties()

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
                            version = state.managerState.version,
                            metadata = updatedMetadata.toMetadata(),
                        )
                    Result(null, UpdateAction(newState, false), session)
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
    private val reEstablishmentMessageSender = ReEstablishmentMessageSender(
        p2pRecordsFactory,
        sessionManagerImpl,
    )
    private val stateManager = StateManagerWrapper(
        stateManager,
        sessionCache,
        stateConvertor,
        sessionManagerImpl.revocationCheckerClient::checkRevocation,
        reEstablishmentMessageSender,
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
                sessionEventListener.dominoTile.coordinatorName,
            ),
            managedChildren =
            setOf(
                sessionManagerImpl.dominoTile.toNamedLifecycle(),
                sessionEventPublisher.dominoTile.toNamedLifecycle(),
                sessionEventListener.dominoTile.toNamedLifecycle(),
            ),
        )
}
