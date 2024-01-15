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
import net.corda.membership.lib.grouppolicy.GroupPolicyConstants
import net.corda.p2p.crypto.protocol.api.AuthenticatedEncryptionSession
import net.corda.p2p.crypto.protocol.api.AuthenticatedSession
import net.corda.p2p.crypto.protocol.api.AuthenticationProtocolResponder
import net.corda.p2p.crypto.protocol.api.Session
import net.corda.p2p.linkmanager.sessions.metadata.SessionMetadata
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

internal class StatefulSessionManagerImpl(
    coordinatorFactory: LifecycleCoordinatorFactory,
    private val stateManager: StateManager,
    private val sessionManagerImpl: SessionManagerImpl,
    private val schemaRegistry: AvroSchemaRegistry,
    private val sessionEncryptionOpsClient: SessionEncryptionOpsClient,
): SessionManager {

    override fun <T> processOutboundMessages(
        wrappedMessages: Collection<T>,
        getMessage: (T) -> AuthenticatedMessageAndKey
    ): Collection<Pair<T, SessionManager.SessionState>> {
        val messageAndKeyMap = wrappedMessages.associateBy { message ->
            getMessage(message).calculateHash()
        }
        val updates = mutableSetOf<State>()
        val sessionStates = stateManager.get(messageAndKeyMap.keys).let { state ->
            messageAndKeyMap.mapNotNull {
                OutboundMessageContext(it.value, state[it.key], getMessage(it.value))
            }
        }
        val resultStates = sessionStates.map { traceAndState ->
            val counterparties = sessionManagerImpl.getSessionCounterpartiesFromMessage(traceAndState.message.message)
            if (counterparties == null) {
                traceAndState to SessionManager.SessionState.CannotEstablishSession
            }
            val state = traceAndState.state ?: return@map sessionNeeded(
                counterparties!!,
                traceAndState.message.message.header.statusFilter,
            )?.let {
                it.second?.let { newState -> updates.add(newState) }
                traceAndState to it.first
            } ?: (traceAndState to SessionManager.SessionState.CannotEstablishSession)
            val metadata = SessionMetadata(state.metadata)
            if (metadata.lastSendExpired()) {
                when (metadata.status) {
                    SessionStatus.SentInitiatorHello, SessionStatus.SentInitiatorHandshake -> {
                        traceAndState to SessionManager.SessionState.SessionAlreadyPending(counterparties!!)
                    }
                    SessionStatus.SessionReady -> {
                        traceAndState to (state.retrieveEstablishedSession(counterparties!!)
                            ?: SessionManager.SessionState.CannotEstablishSession)
                    }
                    else -> traceAndState to SessionManager.SessionState.CannotEstablishSession
                }
            } else {
                when (metadata.status) {
                    SessionStatus.SentInitiatorHello -> {
                        sessionNeeded(
                            counterparties!!,
                            traceAndState.message.message.header.statusFilter,
                        )?.let {
                            updates.add(state.withUpdatedTimestamp())
                            traceAndState to it.first
                        } ?: (traceAndState to SessionManager.SessionState.CannotEstablishSession)
                    }

                    SessionStatus.SentInitiatorHandshake -> {
                        sessionNeeded(
                            counterparties!!,
                            traceAndState.message.message.header.statusFilter,
                        )?.let {
                            updates.add(state.withUpdatedTimestamp())
                            traceAndState to it.first
                        } ?: (traceAndState to SessionManager.SessionState.CannotEstablishSession)
                    }

                    SessionStatus.SessionReady -> {
                        traceAndState to (state.retrieveEstablishedSession(counterparties!!)
                            ?: SessionManager.SessionState.CannotEstablishSession)
                    }

                    else -> traceAndState to SessionManager.SessionState.CannotEstablishSession
                }
            }
        }

        val failures = stateManager.update(updates)

        return resultStates
            .filterNot { failures.containsKey(it.first.message.calculateHash()) }
            .map { it.first.trace to it.second }
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
        return when(sessionManagerImpl.getProtocolMode(counterParties.ourId)) {
            GroupPolicyConstants.PolicyValues.P2PParameters.ProtocolMode.AUTH -> {
                SessionManager.SessionState.SessionEstablished(sessionData as AuthenticatedSession, counterParties)
            }
            GroupPolicyConstants.PolicyValues.P2PParameters.ProtocolMode.AUTH_ENCRYPT -> {
                SessionManager.SessionState.SessionEstablished(sessionData as AuthenticatedEncryptionSession, counterParties)
            }
            else -> null
        }
    }

    private fun sessionNeeded(
        counterParties: SessionManager.SessionCounterparties,
        filter: MembershipStatusFilter,
    ): Pair<SessionManager.SessionState.NewSessionsNeeded, State?>? {
        val initMessage = sessionManagerImpl.genSessionInitMessages(counterParties, 1).firstOrNull() ?: return null
        val message = sessionManagerImpl.linkOutMessagesFromSessionInitMessages(
            counterParties,
            listOf(initMessage),
            filter,
        )?.firstOrNull() ?: return null

        val newMetadata = SessionMetadata(
            sessionId = initMessage.first.sessionId,
            source = counterParties.ourId,
            destination = counterParties.counterpartyId,
            lastSendTimestamp = Instant.now(),
            encryptionKeyId = "",
            encryptionKeyTenant = "",
            status = SessionStatus.SentInitiatorHello,
            expiry = Instant.now() + Duration.ofDays(7)
        )
        val newState = State(
            initMessage.first.sessionId,
            SessionState(message.second, initMessage.first)
                .toAvro(schemaRegistry, sessionEncryptionOpsClient).toByteBuffer().array(),
            metadata = newMetadata.toMetadata()
        )
        return SessionManager.SessionState.NewSessionsNeeded(listOf(message), counterParties) to newState
    }

    private fun State.withUpdatedTimestamp(): State {
        val updatedMetadata = SessionMetadata(metadata).copy(lastSendTimestamp = Instant.now())
        return State(
            key = key,
            value = value,
            version = version,
            metadata = updatedMetadata.toMetadata()
        )
    }

    override fun <T> getSessionsById(
        uuids: Collection<T>,
        getSessionId: (T) -> String
    ): Collection<Pair<T, SessionManager.SessionDirection>> {
        val traceable = uuids.associateBy { getSessionId(it) }
        val sessionsFromCache = uuids.map {
            it to (cachedInboundSessions[getSessionId(it)] ?: cachedOutboundSessions[getSessionId(it)])
        }
        val sessionsNotInCache = sessionsFromCache.filter { it.second == null }.map { it.first to getSessionId(it.first) }
        val inboundSessionsFromStateManager: List<Pair<T, SessionManager.SessionDirection>> =
            stateManager.get(sessionsNotInCache.map { it.second }).entries.mapNotNull { (sessionId, state) ->
            val session = AvroSessionState.fromByteBuffer(ByteBuffer.wrap(state.value))
                .toCorda(
                    schemaRegistry,
                    sessionEncryptionOpsClient,
                    sessionManagerImpl.revocationCheckerClient::checkRevocation,
                ).sessionData as Session
            traceable[sessionId]?.let{
                it to SessionManager.SessionDirection.Inbound(SessionMetadata(state.metadata).toCounterparties(), session)
            }
        }
        val sessionsNotInInboundStateManager =
            (sessionsNotInCache.map { it.first } - inboundSessionsFromStateManager.map { it.first }.toSet()).map {
                getSessionIdFilter(getSessionId(it))
            }
        val outboundSessionsFromStateManager: List<Pair<T, SessionManager.SessionDirection>> =
            stateManager.findByMetadataMatchingAny(sessionsNotInInboundStateManager).entries.mapNotNull { (sessionId, state) ->
                val session = AvroSessionState.fromByteBuffer(ByteBuffer.wrap(state.value))
                    .toCorda(schemaRegistry, sessionEncryptionOpsClient, sessionManagerImpl.revocationCheckerClient::checkRevocation).sessionData as Session
                traceable[sessionId]?.let{
                    it to SessionManager.SessionDirection.Outbound(SessionMetadata(state.metadata).toCounterparties(), session)
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
        /*To be implemented CORE-18631 (process InitiatorHelloMessage, InitiatorHandshakeMessage)
         * and CORE-18630 (process ResponderHello + process ResponderHandshake)
         * */
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
                                SessionMetadata(it.stateUpdate.metadata).toCounterparties(),
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
                                SessionMetadata(it.stateUpdate.metadata).toCounterparties(),
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

    private fun AuthenticatedMessageAndKey.calculateHash(): String {
        val (source, groupId) = message.header.source.let {
            it.x500Name to it.groupId
        }
        val destination = message.header.destination.x500Name
        return SessionStateKey(source, destination, groupId).hash.toHexString()
    }

    private data class SessionStateKey(
        val ourX500Name: String,
        val peerX500Name: String,
        val groupId: String,
    ) {
        val hash: SecureHash by lazy(LazyThreadSafetyMode.PUBLICATION) {
            val s = (ourX500Name + peerX500Name + groupId)
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

    private fun SessionMetadata.toCounterparties(): SessionManager.Counterparties {
        return SessionManager.Counterparties(
            ourId = this.destination,
            counterpartyId = this.source
        )
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
        val metadata = state?.metadata?.let { metadataMap -> SessionMetadata(metadataMap) }
        return when (metadata?.status) {
            null -> {
                sessionManagerImpl.processInitiatorHello(message.initiatorHelloMessage)?.let {
                    (responseMessage, authenticationProtocol) ->
                    val timestamp = Instant.now()
                    val newMetadata = SessionMetadata(
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
        val metadata = state?.metadata?.let { metadataMap -> SessionMetadata(metadataMap) }
        return when (metadata?.status) {
            SessionStatus.SentInitiatorHello -> {
                sessionManagerImpl.processResponderHello(message.responderHelloMessage)?.let {
                        (responseMessage, authenticationProtocol) ->
                    val timestamp = Instant.now()
                    val newMetadata = SessionMetadata(
                        sessionId = message.responderHelloMessage.header.sessionId,
                        source = responseMessage.header.destinationIdentity.toCorda(),
                        destination = responseMessage.header.sourceIdentity.toCorda(),
                        lastSendTimestamp = timestamp,
                        encryptionKeyId = "",
                        encryptionKeyTenant = "",
                        status = SessionStatus.SentInitiatorHandshake,
                        expiry = Instant.now() + Duration.ofDays(7)
                    )
                    val newState = State(
                        message.responderHelloMessage.header.sessionId,
                        SessionState(responseMessage, authenticationProtocol)
                            .toAvro(schemaRegistry, sessionEncryptionOpsClient).toByteBuffer().array(),
                        metadata = newMetadata.toMetadata()
                    )
                    responseMessage to newState
                }
            }
            SessionStatus.SentInitiatorHandshake -> {
                if (metadata.lastSendExpired()) {
                    val timestamp = Instant.now()
                    val updatedMetadata = metadata.copy(lastSendTimestamp = timestamp)
                    val initiatorHandshakeToResend = AvroSessionState.fromByteBuffer(ByteBuffer.wrap(state.value))
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
                    initiatorHandshakeToResend to newState
                } else {
                    null
                }
            }
            else -> null
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
        val metadata = state?.metadata?.let { metadataMap -> SessionMetadata(metadataMap) }
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
                    val newMetadata = SessionMetadata(
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
                        metadata = newMetadata.toMetadata()
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
    ): ProcessHandshakeResult?{
        val metadata = state?.metadata?.let { metadataMap -> SessionMetadata(metadataMap) }
        return when (metadata?.status) {
            SessionStatus.SentResponderHandshake -> {
                sessionManagerImpl.processResponderHandshake(message.responderHandshakeMessage)?.let { (_, session) ->
                    if (session == null) {
                        null
                    } else {
                        val timestamp = Instant.now()
                        val updatedMetadata = metadata.copy(
                            status = SessionStatus.SessionReady, lastSendTimestamp = timestamp
                        )
                        val newState = State(
                            message.responderHandshakeMessage.header.sessionId,
                            SessionState(null, session).toAvro(schemaRegistry, sessionEncryptionOpsClient)
                                .toByteBuffer().array(),
                            metadata = updatedMetadata.toMetadata()
                        )
                        ProcessHandshakeResult(null, newState, session as Session)
                    }
                }
            }
            else -> null
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
            is ResponderHelloMessage -> OutboundSessionMessageContext(this.header!!.sessionId, OutboundSessionMessage.ResponderHelloMessage(this), trace)
            is ResponderHandshakeMessage -> OutboundSessionMessageContext(this.header!!.sessionId, OutboundSessionMessage.ResponderHandshakeMessage(this), trace)
            else -> null
        }
    }

    override val dominoTile = SimpleDominoTile(this::class.java.simpleName, coordinatorFactory)
}