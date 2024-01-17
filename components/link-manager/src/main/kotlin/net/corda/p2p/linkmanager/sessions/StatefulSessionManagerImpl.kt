package net.corda.p2p.linkmanager.sessions

import java.nio.ByteBuffer
import java.time.Duration
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap
import net.corda.crypto.client.SessionEncryptionOpsClient
import net.corda.crypto.core.SecureHashImpl
import net.corda.data.p2p.AuthenticatedMessageAndKey
import net.corda.data.p2p.LinkInMessage
import net.corda.data.p2p.LinkOutMessage
import net.corda.data.p2p.app.MembershipStatusFilter
import net.corda.data.p2p.crypto.InitiatorHandshakeMessage
import net.corda.data.p2p.crypto.InitiatorHelloMessage
import net.corda.data.p2p.crypto.ResponderHandshakeMessage
import net.corda.data.p2p.crypto.ResponderHelloMessage
import net.corda.libs.statemanager.api.MetadataFilter
import net.corda.libs.statemanager.api.Operation
import net.corda.libs.statemanager.api.State
import net.corda.libs.statemanager.api.StateManager
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.domino.logic.SimpleDominoTile
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
import net.corda.p2p.linkmanager.sessions.metadata.OutboundSessionMetadata
import net.corda.p2p.linkmanager.sessions.metadata.SessionStatus
import net.corda.p2p.linkmanager.state.SessionState
import net.corda.p2p.linkmanager.state.SessionState.Companion.toCorda
import net.corda.schema.registry.AvroSchemaRegistry
import net.corda.v5.crypto.DigestAlgorithmName
import net.corda.v5.crypto.SecureHash
import net.corda.virtualnode.HoldingIdentity
import net.corda.virtualnode.toCorda
import org.slf4j.LoggerFactory
import java.security.MessageDigest
import net.corda.data.p2p.state.SessionState as AvroSessionState

@Suppress("TooManyFunctions", "LongParameterList", "LargeClass")
internal class StatefulSessionManagerImpl(
    coordinatorFactory: LifecycleCoordinatorFactory,
    private val stateManager: StateManager,
    private val sessionManagerImpl: SessionManagerImpl,
    private val schemaRegistry: AvroSchemaRegistry,
    private val sessionEncryptionOpsClient: SessionEncryptionOpsClient,
    private val membershipGroupReaderProvider: MembershipGroupReaderProvider,
): SessionManager {

    override fun <T> processOutboundMessages(
        wrappedMessages: Collection<T>,
        getMessage: (T) -> AuthenticatedMessageAndKey
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
            val metadata = state?.let { OutboundSessionMetadata(it.metadata) }
            if (metadata?.lastSendExpired() == false) {
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

        // TODO fix
        return resultStates.map { resultState ->
            val key = resultState.first.state?.metadata?.let {
                val metadata = OutboundSessionMetadata(it)
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
        getSessionId: (T) -> String
    ): Collection<Pair<T, SessionManager.SessionDirection>> {
        val traceable = uuids.associateBy { getSessionId(it) }
        val sessionsFromCache = uuids.map {
            it to (cachedInboundSessions[getSessionId(it)] ?: cachedOutboundSessions[getSessionId(it)])
        }
        val sessionsNotInCache = sessionsFromCache.filter { it.second == null }.associate { it.first to getSessionId(it.first) }
        val inboundSessionsFromStateManager: List<Pair<T, SessionManager.SessionDirection>> =
            stateManager.get(sessionsNotInCache.values).entries.mapNotNull { (sessionId, state) ->
            val session = AvroSessionState.fromByteBuffer(ByteBuffer.wrap(state.value))
                .toCorda(
                    schemaRegistry,
                    sessionEncryptionOpsClient,
                    sessionManagerImpl.revocationCheckerClient::checkRevocation,
                ).sessionData as Session
            traceable[sessionId]?.let{
                it to SessionManager.SessionDirection.Inbound(InboundSessionMetadata(state.metadata).toCounterparties(), session)
            }
        }
        val sessionsNotInInboundStateManager =
            (sessionsNotInCache.keys - inboundSessionsFromStateManager.map { it.first }.toSet()).map {
                getSessionIdFilter(getSessionId(it))
            }
        val outboundSessionsFromStateManager: List<Pair<T, SessionManager.SessionDirection>> =
            stateManager.findByMetadataMatchingAny(sessionsNotInInboundStateManager).entries.mapNotNull { (sessionId, state) ->
                val session = AvroSessionState.fromByteBuffer(ByteBuffer.wrap(state.value)).toCorda(
                    schemaRegistry,
                    sessionEncryptionOpsClient,
                    sessionManagerImpl.revocationCheckerClient::checkRevocation
                ).sessionData as Session

                traceable[sessionId]?.let {
                    it to SessionManager.SessionDirection.Outbound(
                        OutboundSessionMetadata(state.metadata).toCounterparties(), session
                    )
                }
            }

        return sessionsFromCache.mapNotNull { (traceable, sessionDirection) ->
            sessionDirection?.let { traceable to it }
        } + inboundSessionsFromStateManager + outboundSessionsFromStateManager
    }

    override fun <T> processSessionMessages(
        wrappedMessages: Collection<T>,
        getMessage: (T) -> LinkInMessage
    ): Collection<Pair<T, LinkOutMessage?>> {
        val messages = wrappedMessages.map { it to getMessage(it)}
        val result = (processInboundSessionMessages(messages) + processOutboundSessionMessages(messages)).toMutableList()

        stateManager.update(result.map { it.stateUpdate }).map { (failedUpdateSessionId, _) ->
            val toRemove = result.find { it.stateUpdate.key == failedUpdateSessionId }
            result.remove(toRemove)
        }
        return result.map{
            when(it.message?.payload) {
                is ResponderHelloMessage, is ResponderHandshakeMessage -> {
                    it.sessionToCache?.let { sessionToCache ->
                        cachedInboundSessions.put(
                            sessionToCache.sessionId,
                            SessionManager.SessionDirection.Inbound(
                                OutboundSessionMetadata(it.stateUpdate.metadata).toCounterparties(),
                                sessionToCache
                            )
                        )
                    }
                }
                is InitiatorHelloMessage, is InitiatorHandshakeMessage -> {
                    it.sessionToCache?.let { sessionToCache ->
                        cachedOutboundSessions.put(
                            sessionToCache.sessionId,
                            SessionManager.SessionDirection.Outbound(
                                InboundSessionMetadata(it.stateUpdate.metadata).toCounterparties(),
                                sessionToCache
                            )
                        )
                    }
                }
            }

            it.traceable to it.message
        }
    }

    override fun messageAcknowledged(sessionId: String) {
        //To be implemented in CORE-18730
        return
    }

    override fun inboundSessionEstablished(sessionId: String) {
        //Not needed by the Stateful Session Manager
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

    private val logger = LoggerFactory.getLogger(this::class.java)

    private data class InboundSessionMessageContext <T>(
        val sessionId: String,
        val inboundSessionMessage: InboundSessionMessage,
        val trace: T
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

    private sealed class InboundSessionMessage {
        data class InitiatorHelloMessage(
            val initiatorHelloMessage: net.corda.data.p2p.crypto.InitiatorHelloMessage
        ): InboundSessionMessage()
        data class InitiatorHandshakeMessage(
            val initiatorHandshakeMessage: net.corda.data.p2p.crypto.InitiatorHandshakeMessage
        ): InboundSessionMessage()
    }

    private sealed class OutboundSessionMessage {
        data class ResponderHelloMessage(
            val responderHelloMessage: net.corda.data.p2p.crypto.ResponderHelloMessage
        ): OutboundSessionMessage()
        data class ResponderHandshakeMessage(
            val responderHandshakeMessage: net.corda.data.p2p.crypto.ResponderHandshakeMessage
        ): OutboundSessionMessage()
    }

    private data class TraceableResult<T>(
        val traceable: T,
        val message: LinkOutMessage?,
        val stateUpdate: State,
        val sessionToCache: Session?
    )

    private val cachedInboundSessions = ConcurrentHashMap<String, SessionManager.SessionDirection>()

    private val cachedOutboundSessions = ConcurrentHashMap<String, SessionManager.SessionDirection>()

    private fun InboundSessionMetadata.toCounterparties(): SessionManager.Counterparties {
        return SessionManager.Counterparties(
            ourId = this.destination,
            counterpartyId = this.source
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
        val sessionData = AvroSessionState.fromByteBuffer(ByteBuffer.wrap(value))
            .toCorda(
                schemaRegistry,
                sessionEncryptionOpsClient,
                sessionManagerImpl.revocationCheckerClient::checkRevocation
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

        val newMetadata = OutboundSessionMetadata(
            sessionId = initMessage.first.sessionId,
            source = counterParties.ourId,
            destination = counterParties.counterpartyId,
            lastSendTimestamp = Instant.now(),
            encryptionKeyId = "",
            encryptionKeyTenant = "",
            status = SessionStatus.SentInitiatorHello,
            expiry = Instant.now() + Duration.ofDays(7),
            serial = counterParties.serial,
            membershipStatus = counterParties.status,
            communicationWithMgm = counterParties.communicationWithMgm,
        )
        val newState = State(
            initMessage.first.sessionId,
            SessionState(message.second, initMessage.first)
                .toAvro(schemaRegistry, sessionEncryptionOpsClient).toByteBuffer().array(),
            metadata = newMetadata.toMetadata()
        )
        return SessionManager.SessionState.NewSessionsNeeded(listOf(message), counterParties) to newState
    }

    private fun State.replaySessionMessage(): Pair<SessionManager.SessionState.NewSessionsNeeded, State>? {
        val sessionMessage = AvroSessionState.fromByteBuffer(ByteBuffer.wrap(value)).toCorda(
            schemaRegistry, sessionEncryptionOpsClient, sessionManagerImpl.revocationCheckerClient::checkRevocation
        ).message ?: return null
        val updatedMetadata = OutboundSessionMetadata(metadata).copy(lastSendTimestamp = Instant.now())
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
        val states = stateManager.get(messageContexts.map { it.sessionId })
        return messageContexts.mapNotNull {
            val state = states[it.sessionId]
            when (it.inboundSessionMessage) {
                is InboundSessionMessage.InitiatorHelloMessage -> {
                    processInitiatorHello(state, it.inboundSessionMessage)?.let { (message, stateUpdate) ->
                        TraceableResult(it.trace, message, stateUpdate, null)
                    }
                }
                is InboundSessionMessage.InitiatorHandshakeMessage -> {
                    processInitiatorHandshake(state, it.inboundSessionMessage)?.let { (message, stateUpdate, session) ->
                        TraceableResult(it.trace, message, stateUpdate, session)
                    }
                }
            }
        }
    }

    private fun <T> processOutboundSessionMessages(messages: List<Pair<T, LinkInMessage?>>): Collection<TraceableResult<T>> {
        val messageContexts = messages.mapNotNull {
            it.second?.payload?.getSessionIdIfOutboundSessionMessage(it.first)
        }
        val states = stateManager.findByMetadataMatchingAny(messageContexts.map { getSessionIdFilter(it.sessionId) })
        return messageContexts.mapNotNull {
            val state = states[it.sessionId]
            when (it.outboundSessionMessage) {
                is OutboundSessionMessage.ResponderHelloMessage -> {
                    processResponderHello(state, it.outboundSessionMessage)?.let { (message, stateUpdate) ->
                        TraceableResult(it.trace, message, stateUpdate, null)
                    }
                }
                is OutboundSessionMessage.ResponderHandshakeMessage -> {
                    processResponderHandshake(state, it.outboundSessionMessage)?.let { (message, stateUpdate, session) ->
                        TraceableResult(it.trace, message, stateUpdate, session)
                    }
                }
            }
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
        val metadata = state?.metadata?.let { metadataMap -> InboundSessionMetadata(metadataMap) }
        return when (metadata?.status) {
            null -> {
                sessionManagerImpl.processInitiatorHello(message.initiatorHelloMessage)?.let {
                    (responseMessage, authenticationProtocol) ->
                    val timestamp = Instant.now()
                    val newMetadata = InboundSessionMetadata(
                        sessionId = message.initiatorHelloMessage.header.sessionId,
                        source = responseMessage.header.destinationIdentity.toCorda(),
                        destination = responseMessage.header.sourceIdentity.toCorda(),
                        lastSendTimestamp = timestamp,
                        encryptionKeyId = "",
                        encryptionKeyTenant = "",
                        status = SessionStatus.SentResponderHello,
                        expiry = Instant.now() + Duration.ofDays(7)
                    )
                    val newState = State(
                        message.initiatorHelloMessage.header.sessionId,
                        SessionState(responseMessage, authenticationProtocol)
                            .toAvro(schemaRegistry, sessionEncryptionOpsClient).toByteBuffer().array(),
                        metadata = newMetadata.toMetadata()
                    )
                    responseMessage to newState
                }
            }
            SessionStatus.SentResponderHello -> {
                if (metadata.lastSendExpired()) {
                    val timestamp = Instant.now()
                    val updatedMetadata = metadata.copy(lastSendTimestamp = timestamp)
                    val responderHelloToResend = AvroSessionState.fromByteBuffer(ByteBuffer.wrap(state.value))
                        .toCorda(
                            schemaRegistry,
                            sessionEncryptionOpsClient,
                            sessionManagerImpl.revocationCheckerClient::checkRevocation
                        ).message
                    val newState = State(
                        key = state.key,
                        value = state.value,
                        version = state.version,
                        metadata = updatedMetadata.toMetadata()
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
        val metadata = state?.metadata?.let { metadataMap -> OutboundSessionMetadata(metadataMap) }
        return when (metadata?.status) {
            SessionStatus.SentInitiatorHello -> {
                val sessionState = AvroSessionState.fromByteBuffer(ByteBuffer.wrap(state.value)).toCorda(
                    schemaRegistry,
                    sessionEncryptionOpsClient,
                    sessionManagerImpl.revocationCheckerClient::checkRevocation
                ).sessionData as AuthenticationProtocolInitiator
                val counterparties = state.getSessionCounterparties()

                sessionManagerImpl.processResponderHello(
                    counterparties, sessionState, message.responderHelloMessage
                )?.let { (responseMessage, authenticationProtocol) ->
                    val timestamp = Instant.now()
                    val updatedMetadata = metadata.copy(
                        sessionId = message.responderHelloMessage.header.sessionId,
                        source = metadata.source,
                        destination = metadata.destination,
                        lastSendTimestamp = timestamp,
                        encryptionKeyId = "",
                        encryptionKeyTenant = "",
                        status = SessionStatus.SentInitiatorHandshake,
                        expiry = Instant.now() + Duration.ofDays(7)
                    )
                    val newState = State(
                        calculateOutboundSessionKey(
                            counterparties.ourId, counterparties.counterpartyId, counterparties.serial
                        ),
                        SessionState(responseMessage, authenticationProtocol).toAvro(
                            schemaRegistry, sessionEncryptionOpsClient
                        ).toByteBuffer().array(),
                        version = state.version + 1,
                        metadata = updatedMetadata.toMetadata()
                    )
                    responseMessage to newState
                }
            }

            SessionStatus.SentInitiatorHandshake -> {
                if (metadata.lastSendExpired()) {
                    val timestamp = Instant.now()
                    val updatedMetadata = metadata.copy(lastSendTimestamp = timestamp)
                    val initiatorHandshakeToResend =
                        AvroSessionState.fromByteBuffer(ByteBuffer.wrap(state.value)).toCorda(
                            schemaRegistry,
                            sessionEncryptionOpsClient,
                            sessionManagerImpl.revocationCheckerClient::checkRevocation
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
        val session: Session?
    )

    private fun processInitiatorHandshake(
        state: State?,
        message: InboundSessionMessage.InitiatorHandshakeMessage,
    ): ProcessHandshakeResult?{
        val metadata = state?.metadata?.let { metadataMap -> InboundSessionMetadata(metadataMap) }
        return when (metadata?.status) {
            null -> {
                null
            }
            SessionStatus.SentResponderHello -> {
                val sessionData = AvroSessionState.fromByteBuffer(ByteBuffer.wrap(state.value))
                    .toCorda(
                        schemaRegistry,
                        sessionEncryptionOpsClient,
                        sessionManagerImpl.revocationCheckerClient::checkRevocation
                    ).sessionData as AuthenticationProtocolResponder
                sessionManagerImpl.processInitiatorHandshake(sessionData, message.initiatorHandshakeMessage)?.let { responseMessage ->
                    val timestamp = Instant.now()
                    val updatedMetadata = metadata.copy(
                        sessionId = message.initiatorHandshakeMessage.header.sessionId,
                        source = responseMessage.header.sourceIdentity.toCorda(),
                        destination = responseMessage.header.destinationIdentity.toCorda(),
                        lastSendTimestamp = timestamp,
                        encryptionKeyId = "",
                        encryptionKeyTenant = "",
                        status = SessionStatus.SentResponderHandshake,
                        expiry = Instant.now() + Duration.ofDays(7)
                    )
                    val session = sessionData.getSession()
                    val newState = State(
                        message.initiatorHandshakeMessage.header.sessionId,
                        SessionState(responseMessage, session)
                            .toAvro(schemaRegistry, sessionEncryptionOpsClient).toByteBuffer().array(),
                        metadata = updatedMetadata.toMetadata()
                    )
                    ProcessHandshakeResult(responseMessage, newState, session)
                }
            }
            SessionStatus.SentResponderHandshake -> {
                if (metadata.lastSendExpired()) {
                    val timestamp = Instant.now()
                    val updatedMetadata = metadata.copy(lastSendTimestamp = timestamp)
                    val responderHandshakeToResend = AvroSessionState.fromByteBuffer(ByteBuffer.wrap(state.value))
                        .toCorda(
                            schemaRegistry,
                            sessionEncryptionOpsClient,
                            sessionManagerImpl.revocationCheckerClient::checkRevocation
                        ).message
                    val newState = State(
                        key = state.key,
                        value = state.value,
                        version = state.version,
                        metadata = updatedMetadata.toMetadata()
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
        val metadata = state?.metadata?.let { metadataMap -> OutboundSessionMetadata(metadataMap) }
        return when (metadata?.status) {
            SessionStatus.SentInitiatorHandshake -> {
                val sessionState = AvroSessionState.fromByteBuffer(ByteBuffer.wrap(state.value)).toCorda(
                    schemaRegistry,
                    sessionEncryptionOpsClient,
                    sessionManagerImpl.revocationCheckerClient::checkRevocation
                ).sessionData as AuthenticationProtocolInitiator
                val counterparties = state.getSessionCounterparties()

                sessionManagerImpl.processResponderHandshake(
                    message.responderHandshakeMessage, counterparties, sessionState
                )?.let { session ->
                    val timestamp = Instant.now()
                    val updatedMetadata = metadata.copy(
                        status = SessionStatus.SessionReady, lastSendTimestamp = timestamp
                    )
                    val newState = State(
                        calculateOutboundSessionKey(
                            counterparties.ourId, counterparties.counterpartyId, counterparties.serial
                        ),
                        SessionState(null, session).toAvro(schemaRegistry, sessionEncryptionOpsClient).toByteBuffer()
                            .array(),
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
            is InitiatorHelloMessage -> InboundSessionMessageContext(
                this.header!!.sessionId,
                InboundSessionMessage.InitiatorHelloMessage(
                    this,
                ),
                trace,
            )
            is InitiatorHandshakeMessage -> InboundSessionMessageContext(
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
            is ResponderHelloMessage -> OutboundSessionMessageContext(
                this.header!!.sessionId, OutboundSessionMessage.ResponderHelloMessage(this), trace
            )

            is ResponderHandshakeMessage -> OutboundSessionMessageContext(
                this.header!!.sessionId, OutboundSessionMessage.ResponderHandshakeMessage(this), trace
            )

            else -> null
        }
    }

    private fun State.getSessionCounterparties(): SessionManager.SessionCounterparties {
        val metadata = OutboundSessionMetadata(metadata)
        return SessionManager.SessionCounterparties(
            metadata.source,
            metadata.destination,
            metadata.membershipStatus,
            metadata.serial,
            metadata.communicationWithMgm
        )
    }

    override val dominoTile = SimpleDominoTile(this::class.java.simpleName, coordinatorFactory)
}