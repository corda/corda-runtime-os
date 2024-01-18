package net.corda.p2p.linkmanager.sessions

import com.github.benmanes.caffeine.cache.Cache
import com.github.benmanes.caffeine.cache.Caffeine
import net.corda.cache.caffeine.CacheFactoryImpl
import net.corda.crypto.client.SessionEncryptionOpsClient
import net.corda.crypto.core.SecureHashImpl
import net.corda.data.p2p.AuthenticatedMessageAndKey
import net.corda.data.p2p.LinkInMessage
import net.corda.data.p2p.LinkOutMessage
import net.corda.data.p2p.app.MembershipStatusFilter
import net.corda.data.p2p.crypto.ResponderHandshakeMessage as AvroResponderHandshakeMessage
import net.corda.data.p2p.crypto.ResponderHelloMessage as AvroResponderHelloMessage
import net.corda.libs.statemanager.api.MetadataFilter
import net.corda.libs.statemanager.api.Operation
import net.corda.libs.statemanager.api.State
import net.corda.libs.statemanager.api.StateManager
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.LifecycleCoordinatorName
import net.corda.lifecycle.domino.logic.ComplexDominoTile
import net.corda.membership.read.MembershipGroupReaderProvider
import net.corda.p2p.crypto.protocol.api.AuthenticatedEncryptionSession
import net.corda.p2p.crypto.protocol.api.AuthenticatedSession
import net.corda.p2p.crypto.protocol.api.AuthenticationProtocolInitiator
import net.corda.p2p.crypto.protocol.api.AuthenticationProtocolResponder
import net.corda.p2p.crypto.protocol.api.Session
import net.corda.p2p.linkmanager.membership.lookup
import net.corda.p2p.linkmanager.sessions.SessionManagerWarnings.alreadySessionWarning
import net.corda.p2p.linkmanager.sessions.SessionManagerWarnings.invalidSessionStatusError
import net.corda.p2p.linkmanager.sessions.SessionManagerWarnings.noSessionWarning
import net.corda.p2p.linkmanager.sessions.metadata.InboundSessionMetadata
import net.corda.p2p.linkmanager.sessions.metadata.InboundSessionMetadata.Companion.toInbound
import net.corda.p2p.linkmanager.sessions.metadata.OutboundSessionMetadata
import net.corda.p2p.linkmanager.sessions.metadata.OutboundSessionMetadata.Companion.toOutbound
import net.corda.p2p.linkmanager.sessions.metadata.SessionStatus
import net.corda.p2p.linkmanager.state.SessionState
import net.corda.utilities.time.Clock
import net.corda.v5.crypto.DigestAlgorithmName
import net.corda.v5.crypto.SecureHash
import net.corda.virtualnode.HoldingIdentity
import net.corda.virtualnode.toCorda
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.security.MessageDigest
import java.time.Duration
import net.corda.data.p2p.crypto.InitiatorHandshakeMessage as AvroInitiatorHandshakeMessage
import net.corda.data.p2p.crypto.InitiatorHelloMessage as AvroInitiatorHelloMessage

@Suppress("TooManyFunctions", "LongParameterList", "LargeClass")
internal class StatefulSessionManagerImpl(
    coordinatorFactory: LifecycleCoordinatorFactory,
    private val stateManager: StateManager,
    private val sessionManagerImpl: SessionManagerImpl,
    private val stateConvertor: StateConvertor,
    private val clock: Clock,
    private val membershipGroupReaderProvider: MembershipGroupReaderProvider,
) : SessionManager {

    private companion object {
        const val CACHE_SIZE = 10_000L
        val SESSION_VALIDITY_PERIOD: Duration = Duration.ofDays(7)
        val logger: Logger = LoggerFactory.getLogger(StatefulSessionManagerImpl::class.java)
    }

    override fun <T> processOutboundMessages(
        wrappedMessages: Collection<T>,
        getMessage: (T) -> AuthenticatedMessageAndKey,
    ): Collection<Pair<T, SessionManager.SessionState>> {
        val messagesWithoutKey = mutableSetOf<T>()
        val messageAndKeyMap = wrappedMessages.associate {
            val messageHeader = getMessage(it).message.header
            val serial = getCounterpartySerial(
                messageHeader.source.toCorda(),
                messageHeader.destination.toCorda(),
                messageHeader.statusFilter
            )
            if (serial == null) {
                logger.warn(
                    "Cannot establish session for message ${messageHeader.messageId}: Failed to look up counterparty."
                )
                messagesWithoutKey.add(it)
                it to null
            } else {
                it to calculateOutboundSessionKey(
                    messageHeader.source.toCorda(), messageHeader.destination.toCorda(), serial
                )
            }
        }
        val updates = mutableSetOf<State>()
        val sessionStates = stateManager.get(messageAndKeyMap.values.filterNotNull()).let { state ->
            messageAndKeyMap.mapNotNull {
                OutboundMessageContext(it.key, state[it.value], getMessage(it.key))
            }
        }
        val resultStates = sessionStates.map { traceAndState ->
            val counterparties = sessionManagerImpl.getSessionCounterpartiesFromMessage(traceAndState.message.message)
            if (counterparties == null) {
                traceAndState to SessionManager.SessionState.CannotEstablishSession
            }
            val state = traceAndState.state
            val metadata = state?.metadata?.toOutbound()
            if (metadata?.lastSendExpired(clock) == false) {
                when (metadata.status) {
                    SessionStatus.SentInitiatorHello, SessionStatus.SentInitiatorHandshake -> {
                        traceAndState to SessionManager.SessionState.SessionAlreadyPending(counterparties!!)
                    }

                    SessionStatus.SessionReady -> {
                        traceAndState to (state.retrieveEstablishedSession(counterparties!!)
                            ?: SessionManager.SessionState.CannotEstablishSession)
                    }

                    SessionStatus.SentResponderHello, SessionStatus.SentResponderHandshake -> {
                        logger.invalidSessionStatusError(
                            traceAndState.message::class.java.simpleName,
                            metadata.sessionId,
                            metadata.status.toString()
                        )
                        traceAndState to SessionManager.SessionState.CannotEstablishSession
                    }
                }
            } else {
                when (metadata?.status) {
                    null -> {
                        newSessionNeeded(
                            counterparties!!,
                            traceAndState.message.message.header.statusFilter,
                        )?.let {
                            updates.add(it.second)
                            traceAndState to it.first
                        } ?: (traceAndState to SessionManager.SessionState.CannotEstablishSession)
                    }

                    SessionStatus.SentInitiatorHello, SessionStatus.SentInitiatorHandshake -> {
                        state.replaySessionMessage()?.let {
                            updates.add(it.second)
                            traceAndState to it.first
                        } ?: (traceAndState to SessionManager.SessionState.CannotEstablishSession)
                    }

                    SessionStatus.SessionReady -> {
                        traceAndState to (state.retrieveEstablishedSession(counterparties!!)
                            ?: SessionManager.SessionState.CannotEstablishSession)
                    }

                    SessionStatus.SentResponderHello, SessionStatus.SentResponderHandshake -> {
                        logger.invalidSessionStatusError(
                            traceAndState.message::class.java.simpleName,
                            metadata.sessionId,
                            metadata.status.toString()
                        )
                        traceAndState to SessionManager.SessionState.CannotEstablishSession
                    }
                }
            }
        }

        val failures = stateManager.update(updates).also {
            logger.warn("Failed to update session states for the following keys: ${it.keys}.")
        }

        // TODO - return appropriate state for failed updates
        return resultStates.map { resultState ->
            val key = resultState.first.state?.metadata?.let {
                val metadata = it.toOutbound()
                calculateOutboundSessionKey(metadata.source, metadata.destination, metadata.serial)
            }
            if (failures.containsKey(key)) {
                resultState.first.trace to SessionManager.SessionState.CannotEstablishSession
            } else {
                resultState.first.trace to resultState.second
            }
        } + messagesWithoutKey.map { it to SessionManager.SessionState.CannotEstablishSession }
    }

    override fun <T> getSessionsById(
        uuids: Collection<T>,
        getSessionId: (T) -> String,
    ): Collection<Pair<T, SessionManager.SessionDirection>> {
        val traceable = uuids.associateBy { getSessionId(it) }
        val sessionFromInboundCache = cachedInboundSessions.getAllPresent(traceable.keys)
        val allCached = sessionFromInboundCache +
                cachedOutboundSessions.getAllPresent((traceable - sessionFromInboundCache.keys).keys)
        val sessionIdsNotInCache = traceable - allCached.keys
        val inboundSessionsFromStateManager: List<Pair<T, SessionManager.SessionDirection>> =
            if (sessionIdsNotInCache.isEmpty()) {
                emptyList()
            } else {
                stateManager.get(sessionIdsNotInCache.keys).entries.mapNotNull { (sessionId, state) ->
                    val session = stateConvertor.toCordaSessionState(
                        state,
                        sessionManagerImpl.revocationCheckerClient::checkRevocation,
                    ).sessionData as? Session
                    session?.let {
                        sessionIdsNotInCache[sessionId]?.let {
                            val inboundSession = SessionManager.SessionDirection.Inbound(
                                state.metadata.toInbound().toCounterparties(), session
                            )
                            cachedInboundSessions.put(sessionId, inboundSession)
                            it to inboundSession
                        }
                    }
                }
            }
        val sessionsNotInInboundStateManager =
            (sessionIdsNotInCache.values - inboundSessionsFromStateManager.map { it.first }.toSet()).map {
                getSessionIdFilter(getSessionId(it))
            }
        val outboundSessionsFromStateManager: List<Pair<T, SessionManager.SessionDirection>> =
            if (sessionsNotInInboundStateManager.isEmpty()) {
                emptyList()
            } else {
                stateManager.findByMetadataMatchingAny(sessionsNotInInboundStateManager).entries.mapNotNull { (sessionId, state) ->
                    val session = stateConvertor.toCordaSessionState(
                        state,
                        sessionManagerImpl.revocationCheckerClient::checkRevocation,
                    ).sessionData as? Session
                    session?.let {
                        sessionIdsNotInCache[sessionId]?.let {
                            val outboundSession = SessionManager.SessionDirection.Outbound(
                                state.metadata.toOutbound().toCounterparties(), session
                            )
                            cachedInboundSessions.put(sessionId, outboundSession)
                            it to outboundSession
                        }
                    }
                }
            }

        return allCached.mapNotNull { (sessionId, sessionDirection) ->
            traceable[sessionId]?.let { it to sessionDirection }
        } + inboundSessionsFromStateManager + outboundSessionsFromStateManager
    }

    override fun <T> processSessionMessages(
        wrappedMessages: Collection<T>,
        getMessage: (T) -> LinkInMessage,
    ): Collection<Pair<T, LinkOutMessage?>> {
        val messages = wrappedMessages.map { it to getMessage(it)}
        val results = (processInboundSessionMessages(messages) + processOutboundSessionMessages(messages)).toMutableList()

        val failedUpdate = stateManager.update(results.mapNotNull { it.result?.stateUpdate })
            .keys.onEach {
                logger.warn("Failed to update the state of session $it")
            }

        return results.mapNotNull { result ->
            if (failedUpdate.contains(result.result?.stateUpdate?.key)) {
                null
            } else {
                result
            }
        }.onEach { result ->
            when (result.result?.message?.payload) {
                is AvroResponderHelloMessage, is AvroResponderHandshakeMessage -> {
                    result.result.sessionToCache?.let { sessionToCache ->
                        cachedInboundSessions.put(
                            sessionToCache.sessionId,
                            SessionManager.SessionDirection.Inbound(
                                result.result.stateUpdate.metadata.toInbound().toCounterparties(),
                                sessionToCache,
                            ),
                        )
                    }
                }
                is AvroInitiatorHelloMessage, is AvroInitiatorHandshakeMessage -> {
                    result.result.sessionToCache?.let { sessionToCache ->
                        cachedOutboundSessions.put(
                            sessionToCache.sessionId,
                            SessionManager.SessionDirection.Inbound(
                                result.result.stateUpdate.metadata.toInbound().toCounterparties(),
                                sessionToCache,
                            ),
                        )
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

    override fun dataMessageReceived(sessionId: String, source: HoldingIdentity, destination: HoldingIdentity) {
        // Not needed by the Stateful Session Manager
        return
    }

    override fun dataMessageSent(session: Session) {
        // Not needed by the Stateful Session Manager
        return
    }

    private data class InboundSessionMessageContext<T>(
        val sessionId: String,
        val inboundSessionMessage: InboundSessionMessage,
        val trace: T,
    )

    private data class OutboundSessionMessageContext <T>(
        val sessionId: String,
        val outboundSessionMessage: OutboundSessionMessage,
        val trace: T
    )

    private data class OutboundMessageContext<T>(
        val trace: T, val state: State?, val message: AuthenticatedMessageAndKey
    )

    private fun calculateOutboundSessionKey(
        source: HoldingIdentity, destination: HoldingIdentity, serial: Long
    ) = SessionCounterpartiesKey(source, destination, serial).hash.toHexString()

    private fun getCounterpartySerial(
        source: HoldingIdentity, destination: HoldingIdentity, status: MembershipStatusFilter
    ): Long? = membershipGroupReaderProvider.lookup(source, destination, status)?.serial

    private data class SessionCounterpartiesKey(
        override val ourId: HoldingIdentity,
        override val counterpartyId: HoldingIdentity,
        val serial: Long,
    ): SessionManager.BaseCounterparties {
        val hash: SecureHash by lazy(LazyThreadSafetyMode.PUBLICATION) {
            val s = (ourId.x500Name.toString() + counterpartyId.x500Name.toString() + serial.toString())
            val digest: MessageDigest = MessageDigest.getInstance(DigestAlgorithmName.SHA2_256.name)
            val hash: ByteArray = digest.digest(s.toByteArray())
            SecureHashImpl(DigestAlgorithmName.SHA2_256.name, hash)
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
            val responderHelloMessage: AvroResponderHelloMessage
        ): OutboundSessionMessage
        data class ResponderHandshakeMessage(
            val responderHandshakeMessage: AvroResponderHandshakeMessage
        ): OutboundSessionMessage
    }

    private data class TraceableResult<T>(
        val traceable: T,
        val result: Result?,
    )
    private data class Result(
        val message: LinkOutMessage?,
        val stateUpdate: State,
        val sessionToCache: Session?,
    )
    private val cachedInboundSessions: Cache<String, SessionManager.SessionDirection> = CacheFactoryImpl().build(
        "P2P-inbound-sessions-cache",
        Caffeine.newBuilder()
            .maximumSize(CACHE_SIZE),
    )

    private val cachedOutboundSessions: Cache<String, SessionManager.SessionDirection> = CacheFactoryImpl().build(
        "P2P-outbound-sessions-cache",
        Caffeine.newBuilder()
            .maximumSize(CACHE_SIZE),
    )

    private fun InboundSessionMetadata.toCounterparties(): SessionManager.Counterparties {
        return SessionManager.Counterparties(
            ourId = this.destination,
            counterpartyId = this.source,
        )
    }

    private fun OutboundSessionMetadata.toCounterparties(): SessionManager.Counterparties {
        return SessionManager.Counterparties(
            ourId = this.destination,
            counterpartyId = this.source
        )
    }

    private fun State.retrieveEstablishedSession(
        counterParties: SessionManager.SessionCounterparties
    ): SessionManager.SessionState.SessionEstablished? {
        val sessionData = stateConvertor.toCordaSessionState(
            this,
            sessionManagerImpl.revocationCheckerClient::checkRevocation,
        ).sessionData
        return when (sessionData) {
            is AuthenticatedSession, is AuthenticatedEncryptionSession ->
                SessionManager.SessionState.SessionEstablished(sessionData as Session, counterParties)

            else -> null
        }
    }

    private fun newSessionNeeded(
        counterParties: SessionManager.SessionCounterparties,
        filter: MembershipStatusFilter,
    ): Pair<SessionManager.SessionState.NewSessionsNeeded, State>? {
        val initMessage = sessionManagerImpl.genSessionInitMessages(counterParties, 1).firstOrNull() ?: return null
        val message = sessionManagerImpl.linkOutMessagesFromSessionInitMessages(
            counterParties,
            listOf(initMessage),
            filter,
        )?.firstOrNull() ?: return null

        val timestamp = clock.instant()
        val newMetadata = OutboundSessionMetadata(
            sessionId = initMessage.first.sessionId,
            source = counterParties.ourId,
            destination = counterParties.counterpartyId,
            lastSendTimestamp = timestamp,
            status = SessionStatus.SentInitiatorHello,
            expiry = timestamp + SESSION_VALIDITY_PERIOD,
            serial = counterParties.serial,
            membershipStatus = counterParties.status,
            communicationWithMgm = counterParties.communicationWithMgm,
        )
        val newState = State(
            initMessage.first.sessionId,
            stateConvertor.toStateByteArray(SessionState(message.second, initMessage.first)),
            metadata = newMetadata.toMetadata()
        )
        return SessionManager.SessionState.NewSessionsNeeded(listOf(message), counterParties) to newState
    }

    private fun State.replaySessionMessage(): Pair<SessionManager.SessionState.NewSessionsNeeded, State>? {
        val sessionMessage = stateConvertor.toCordaSessionState(
            this,
            sessionManagerImpl.revocationCheckerClient::checkRevocation,
        ).message ?: return null
        val updatedMetadata = metadata.toOutbound().copy(lastSendTimestamp = clock.instant())
        val updatedState = State(
            key, value, version = version + 1, metadata = updatedMetadata.toMetadata()
        )
        return SessionManager.SessionState.NewSessionsNeeded(
            listOf(updatedMetadata.sessionId to sessionMessage), updatedState.getSessionCounterparties()
        ) to updatedState
    }

    private fun <T> processInboundSessionMessages(messages: List<Pair<T, LinkInMessage?>>): Collection<TraceableResult<T>> {
        val messageContexts = messages.mapNotNull {
            it.second?.payload?.getSessionIdIfInboundSessionMessage(it.first)
        }
        if (messageContexts.isEmpty()) {
            return emptyList()
        }
        val states = stateManager.get(messageContexts.map { it.sessionId })
        return messageContexts.map {
            val state = states[it.sessionId]
            val result = when (it.inboundSessionMessage) {
                is InboundSessionMessage.InitiatorHelloMessage -> {
                    processInitiatorHello(state, it.inboundSessionMessage)?.let { (message, stateUpdate) ->
                        Result(message, stateUpdate, null)
                    }
                }
                is InboundSessionMessage.InitiatorHandshakeMessage -> {
                    processInitiatorHandshake(state, it.inboundSessionMessage)?.let { (message, stateUpdate, session) ->
                        Result(message, stateUpdate, session)
                    }
                }
            }
            TraceableResult(it.trace, result)
        }
    }

    private fun <T> processOutboundSessionMessages(messages: List<Pair<T, LinkInMessage?>>): Collection<TraceableResult<T>> {
        val messageContexts = messages.mapNotNull {
            it.second?.payload?.getSessionIdIfOutboundSessionMessage(it.first)
        }
        if (messageContexts.isEmpty()) {
            return emptyList()
        }
        val states = stateManager.findByMetadataMatchingAny(messageContexts.map { getSessionIdFilter(it.sessionId) })
        return messageContexts.map {
            val state = states[it.sessionId]
            val result = when (it.outboundSessionMessage) {
                is OutboundSessionMessage.ResponderHelloMessage -> {
                    processResponderHello(state, it.outboundSessionMessage)?.let { (message, stateUpdate) ->
                        Result(message, stateUpdate, null)
                    }
                }
                is OutboundSessionMessage.ResponderHandshakeMessage -> {
                    processResponderHandshake(state, it.outboundSessionMessage)?.let { (message, stateUpdate, session) ->
                        Result(message, stateUpdate, session)
                    }
                }
            }
            TraceableResult(it.trace, result)
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
                    val newMetadata = InboundSessionMetadata(
                        source = responseMessage.header.destinationIdentity.toCorda(),
                        destination = responseMessage.header.sourceIdentity.toCorda(),
                        lastSendTimestamp = timestamp,
                        status = SessionStatus.SentResponderHello,
                        expiry = timestamp + SESSION_VALIDITY_PERIOD,
                    )
                    val newState = State(
                        message.initiatorHelloMessage.header.sessionId,
                        stateConvertor.toStateByteArray(SessionState(responseMessage, authenticationProtocol)),
                        version = 0,
                        metadata = newMetadata.toMetadata(),
                    )
                    responseMessage to newState
                }
            }
            SessionStatus.SentResponderHello -> {
                if (metadata.lastSendExpired(clock)) {
                    val timestamp = clock.instant()
                    val updatedMetadata = metadata.copy(lastSendTimestamp = timestamp)
                    val responderHelloToResend = stateConvertor.toCordaSessionState(
                        state,
                        sessionManagerImpl.revocationCheckerClient::checkRevocation,
                    ).message
                    val newState = State(
                        key = state.key,
                        value = state.value,
                        version = state.version + 1,
                        metadata = updatedMetadata.toMetadata(),
                    )
                    responderHelloToResend to newState
                } else {
                    null
                }
            }
            SessionStatus.SentResponderHandshake -> {
                null
            }

            else -> null
        }
    }

    private fun processResponderHello(
        state: State?,
        message: OutboundSessionMessage.ResponderHelloMessage,
    ): Pair<LinkOutMessage?, State>? {
        val metadata = state?.metadata?.toOutbound()
        return when (metadata?.status) {
            SessionStatus.SentInitiatorHello -> {
                val sessionState = stateConvertor.toCordaSessionState(
                    state,
                    sessionManagerImpl.revocationCheckerClient::checkRevocation,
                ).sessionData as AuthenticationProtocolInitiator
                val counterparties = state.getSessionCounterparties()

                sessionManagerImpl.processResponderHello(
                    counterparties, sessionState, message.responderHelloMessage
                )?.let { (responseMessage, authenticationProtocol) ->
                    val timestamp = clock.instant()
                    val updatedMetadata = metadata.copy(
                        sessionId = message.responderHelloMessage.header.sessionId,
                        source = metadata.source,
                        destination = metadata.destination,
                        lastSendTimestamp = timestamp,
                        status = SessionStatus.SentInitiatorHandshake,
                        expiry = timestamp + SESSION_VALIDITY_PERIOD
                    )
                    val newState = State(
                        calculateOutboundSessionKey(
                            counterparties.ourId, counterparties.counterpartyId, counterparties.serial
                        ),
                        stateConvertor.toStateByteArray(SessionState(responseMessage, authenticationProtocol)),
                        version = state.version + 1,
                        metadata = updatedMetadata.toMetadata()
                    )
                    responseMessage to newState
                }
            }

            SessionStatus.SentInitiatorHandshake -> {
                if (metadata.lastSendExpired(clock)) {
                    val updatedMetadata = metadata.copy(lastSendTimestamp = clock.instant())
                    val initiatorHandshakeToResend = stateConvertor.toCordaSessionState(
                        state,
                        sessionManagerImpl.revocationCheckerClient::checkRevocation,
                    ).message
                    val newState = State(
                        key = state.key,
                        value = state.value,
                        version = state.version + 1,
                        metadata = updatedMetadata.toMetadata()
                    )
                    initiatorHandshakeToResend to newState
                } else {
                    null
                }
            }

            SessionStatus.SentResponderHello, SessionStatus.SentResponderHandshake -> {
                logger.invalidSessionStatusError(
                    message::class.java.simpleName,
                    message.responderHelloMessage.header.sessionId,
                    metadata.status.toString()
                )
                null
            }

            SessionStatus.SessionReady -> {
                logger.alreadySessionWarning(
                    message::class.java.simpleName, message.responderHelloMessage.header.sessionId
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
            SessionStatus.SentResponderHello -> {
                val sessionData = stateConvertor.toCordaSessionState(
                    state,
                    sessionManagerImpl.revocationCheckerClient::checkRevocation,
                ).sessionData as? AuthenticationProtocolResponder
                if (sessionData == null) {
                    logger.warn(
                        "Session ${state.key} has status SentResponderHello by the saved data is" +
                            " not AuthenticationProtocolResponder.",
                    )
                    return null
                }
                sessionManagerImpl.processInitiatorHandshake(sessionData, message.initiatorHandshakeMessage)?.let { responseMessage ->
                    val timestamp = clock.instant()
                    val newMetadata = InboundSessionMetadata(
                        source = responseMessage.header.sourceIdentity.toCorda(),
                        destination = responseMessage.header.destinationIdentity.toCorda(),
                        lastSendTimestamp = timestamp,
                        status = SessionStatus.SentResponderHandshake,
                        expiry = timestamp + SESSION_VALIDITY_PERIOD,
                    )
                    val session = sessionData.getSession()
                    val newState = State(
                        message.initiatorHandshakeMessage.header.sessionId,
                        stateConvertor.toStateByteArray(SessionState(responseMessage, session)),
                        version = state.version + 1,
                        metadata = newMetadata.toMetadata(),
                    )
                    ProcessHandshakeResult(responseMessage, newState, session)
                }
            }
            SessionStatus.SentResponderHandshake -> {
                if (metadata.lastSendExpired(clock)) {
                    val timestamp = clock.instant()
                    val updatedMetadata = metadata.copy(lastSendTimestamp = timestamp)
                    val responderHandshakeToResend = stateConvertor.toCordaSessionState(
                        state,
                        sessionManagerImpl.revocationCheckerClient::checkRevocation,
                    ).message
                    val newState = State(
                        key = state.key,
                        value = state.value,
                        version = state.version + 1,
                        metadata = updatedMetadata.toMetadata(),
                    )
                    ProcessHandshakeResult(responderHandshakeToResend, newState, null)
                } else {
                    null
                }
            }
            else -> null
        }
    }

    private fun processResponderHandshake(
        state: State?,
        message: OutboundSessionMessage.ResponderHandshakeMessage,
    ): ProcessHandshakeResult? {
        val metadata = state?.metadata?.toOutbound()
        return when (metadata?.status) {
            SessionStatus.SentInitiatorHandshake -> {
                val sessionState = stateConvertor.toCordaSessionState(
                    state,
                    sessionManagerImpl.revocationCheckerClient::checkRevocation,
                ).sessionData as AuthenticationProtocolInitiator
                val counterparties = state.getSessionCounterparties()

                sessionManagerImpl.processResponderHandshake(
                    message.responderHandshakeMessage, counterparties, sessionState
                )?.let { session ->
                    val updatedMetadata = metadata.copy(
                        status = SessionStatus.SessionReady,
                        lastSendTimestamp = clock.instant()
                    )
                    val newState = State(
                        calculateOutboundSessionKey(
                            counterparties.ourId, counterparties.counterpartyId, counterparties.serial
                        ),
                        stateConvertor.toStateByteArray(SessionState(null, session)),
                        version = state.version + 1,
                        metadata = updatedMetadata.toMetadata()
                    )
                    ProcessHandshakeResult(null, newState, session)
                }
            }

            SessionStatus.SentInitiatorHello, SessionStatus.SentResponderHandshake, SessionStatus.SentResponderHello -> {
                logger.invalidSessionStatusError(
                    message::class.java.simpleName,
                    message.responderHandshakeMessage.header.sessionId,
                    metadata.status.toString()
                )
                null
            }

            SessionStatus.SessionReady -> {
                logger.alreadySessionWarning(
                    message::class.java.simpleName, message.responderHandshakeMessage.header.sessionId
                )
                null
            }

            null -> {
                logger.noSessionWarning(
                    message::class.java.simpleName, message.responderHandshakeMessage.header.sessionId
                )
                null
            }
        }
    }

    private fun <T> Any.getSessionIdIfInboundSessionMessage(trace: T): InboundSessionMessageContext<T>? {
        return when (this) {
            is AvroInitiatorHelloMessage -> InboundSessionMessageContext(
                this.header!!.sessionId,
                InboundSessionMessage.InitiatorHelloMessage(
                    this,
                ),
                trace,
            )
            is AvroInitiatorHandshakeMessage -> InboundSessionMessageContext(
                this.header!!.sessionId,
                InboundSessionMessage.InitiatorHandshakeMessage(
                    this,
                ),
                trace,
            )
            else -> null
        }
    }

    private fun <T> Any.getSessionIdIfOutboundSessionMessage(trace: T): OutboundSessionMessageContext<T>? {
        return when (this) {
            is AvroResponderHelloMessage -> OutboundSessionMessageContext(
                this.header!!.sessionId, OutboundSessionMessage.ResponderHelloMessage(this), trace
            )

            is AvroResponderHandshakeMessage -> OutboundSessionMessageContext(
                this.header!!.sessionId, OutboundSessionMessage.ResponderHandshakeMessage(this), trace
            )

            else -> null
        }
    }

    private fun State.getSessionCounterparties(): SessionManager.SessionCounterparties {
        val metadata = this.metadata.toOutbound()
        return SessionManager.SessionCounterparties(
            metadata.source,
            metadata.destination,
            metadata.membershipStatus,
            metadata.serial,
            metadata.communicationWithMgm
        )
    }

    override val dominoTile = ComplexDominoTile(
        this::class.java.simpleName,
        coordinatorFactory,
        dependentChildren = setOf(
            stateManager.name,
            sessionManagerImpl.dominoTile.coordinatorName,
            LifecycleCoordinatorName.forComponent<SessionEncryptionOpsClient>(),
        ),
        managedChildren = setOf(
            sessionManagerImpl.dominoTile.toNamedLifecycle(),
        ),
    )
}
