package net.corda.p2p.linkmanager.sessions

import java.nio.ByteBuffer
import java.time.Duration
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap
import net.corda.crypto.client.SessionEncryptionOpsClient
import net.corda.data.p2p.AuthenticatedMessageAndKey
import net.corda.data.p2p.LinkInMessage
import net.corda.data.p2p.LinkOutMessage
import net.corda.data.p2p.crypto.InitiatorHandshakeMessage
import net.corda.data.p2p.crypto.InitiatorHelloMessage
import net.corda.libs.statemanager.api.State
import net.corda.libs.statemanager.api.StateManager
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.domino.logic.SimpleDominoTile
import net.corda.p2p.crypto.protocol.api.AuthenticationProtocolResponder
import net.corda.p2p.crypto.protocol.api.Session
import net.corda.p2p.linkmanager.sessions.metadata.InboundSessionMetadata
import net.corda.p2p.linkmanager.sessions.metadata.InboundSessionStatus
import net.corda.p2p.linkmanager.state.SessionState
import net.corda.p2p.linkmanager.state.SessionState.Companion.toCorda
import net.corda.schema.registry.AvroSchemaRegistry
import net.corda.virtualnode.HoldingIdentity
import net.corda.virtualnode.toCorda
import org.slf4j.LoggerFactory
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
        return emptyList()
    }

    override fun <T> getSessionsById(
        uuids: Collection<T>,
        getSessionId: (T) -> String
    ): Collection<Pair<T, SessionManager.SessionDirection>> {
        val traceable = uuids.associateBy { getSessionId(it) }
        val sessionFromCache = uuids.map { it to cachedInboundSessions[getSessionId(it)] }
        val sessionsIdsNotInCache = sessionFromCache.filter { it.second == null }.map { getSessionId(it.first) }
        val inboundSessionsFromStateManager: List<Pair<T, SessionManager.SessionDirection>> =
            stateManager.get(sessionsIdsNotInCache).entries.mapNotNull { (sessionId, state) ->
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
        // In CORE-18630 check cache/state manager for outbound sessions if not found for inbound sessions.
        // We should avoid as unnecessary reads of the state manager, so first check the inbound and outbound session cache,
        // then the state manager for an inbound session (query with session ID), then the state manager for an outbound session
        // (query for sessionId in metadata).

        return sessionFromCache.mapNotNull { (traceable, sessionDirection) ->
            sessionDirection?.let { traceable to it }
        } + inboundSessionsFromStateManager
    }

    override fun <T> processSessionMessages(
        wrappedMessages: Collection<T>,
        getMessage: (T) -> LinkInMessage
    ): Collection<Pair<T, LinkOutMessage?>> {
        /*To be implemented CORE-18631 (process InitiatorHelloMessage, InitiatorHandshakeMessage)
         * and CORE-18630 (process ResponderHello + process ResponderHandshake)
         * */
        val messages = wrappedMessages.map { it to getMessage(it)}
        processInboundSessionMessages(messages)
        return emptyList()
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

    private sealed class InboundSessionMessage {
        data class InitiatorHelloMessage(
            val initiatorHelloMessage: net.corda.data.p2p.crypto.InitiatorHelloMessage
        ): InboundSessionMessage()
        data class InitiatorHandshakeMessage(
            val initiatorHandshakeMessage: net.corda.data.p2p.crypto.InitiatorHandshakeMessage
        ): InboundSessionMessage()
    }

    private data class TraceableResult<T>(
        val traceable: T,
        val message: LinkOutMessage,
        val stateUpdate: State,
        val sessionToCache: Session?
    )

    private val cachedInboundSessions = ConcurrentHashMap<String, SessionManager.SessionDirection>()

    private fun InboundSessionMetadata.toCounterparties(): SessionManager.Counterparties {
        return SessionManager.Counterparties(
            ourId = this.destination,
            counterpartyId = this.source
        )
    }

    private fun <T> processInboundSessionMessages(messages: List<Pair<T, LinkInMessage?>>): Collection<Pair<T, LinkOutMessage?>> {
        val messageContexts = messages.mapNotNull {
            it.second?.payload?.getSessionIdIfInboundSessionMessage(it.first)
        }
        val states = stateManager.get(messageContexts.map { it.sessionId })
        val result = messageContexts.mapNotNull {
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
        }.toMutableList()

        stateManager.update(result.map { it.stateUpdate }).map { (failedUpdateSessionId, _) ->
            val toRemove = result.find { it.stateUpdate.key == failedUpdateSessionId }
            result.remove(toRemove)
        }
        return result.map{
            it.sessionToCache?.let { sessionToCache ->
                cachedInboundSessions.put(
                    sessionToCache.sessionId,
                    SessionManager.SessionDirection.Inbound(
                        InboundSessionMetadata(it.stateUpdate.metadata).toCounterparties(),
                        sessionToCache
                    )
                )
            }
            it.traceable to it.message
        }
    }

    /**
     * TODO Refactor SessionManagerImpl to move logic needed here i.e. create an ResponderHello from an InitiatorHello
     * into a new component. This component should not store the AuthenticationProtocol in an in memory map or replay session
     * messages.
     */
    private fun processInitiatorHello(
        state: State?,
        message: InboundSessionMessage.InitiatorHelloMessage,
    ): Pair<LinkOutMessage, State>? {
        val metadata = state?.metadata?.let { metadataMap -> InboundSessionMetadata(metadataMap) }
        return when (metadata?.status) {
            null -> {
                sessionManagerImpl.processInitiatorHello(message.initiatorHelloMessage)?.let {
                    (responseMessage, authenticationProtocol) ->
                    val timestamp = Instant.now()
                    val newMetadata = InboundSessionMetadata(
                        source = responseMessage.header.destinationIdentity.toCorda(),
                        destination = responseMessage.header.sourceIdentity.toCorda(),
                        lastSendTimestamp = timestamp,
                        encryptionKeyId = "",
                        encryptionKeyTenant = "",
                        status = InboundSessionStatus.SentResponderHello,
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
            InboundSessionStatus.SentResponderHello -> {
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
            InboundSessionStatus.SentResponderHandshake -> {
                null
            }
        }
    }

    private data class ProcessInitiatorHandshakeResult(
        val responseMessage: LinkOutMessage,
        val stateToUpdate: State,
        val session: Session?
    )

    private fun processInitiatorHandshake(
        state: State?,
        message: InboundSessionMessage.InitiatorHandshakeMessage,
    ): ProcessInitiatorHandshakeResult?{
        val metadata = state?.metadata?.let { metadataMap -> InboundSessionMetadata(metadataMap) }
        return when (metadata?.status) {
            null -> {
                null
            }
            InboundSessionStatus.SentResponderHello -> {
                val sessionData = AvroSessionState.fromByteBuffer(ByteBuffer.wrap(state.value))
                    .toCorda(
                        schemaRegistry,
                        sessionEncryptionOpsClient,
                        sessionManagerImpl.revocationCheckerClient::checkRevocation
                    ).sessionData as AuthenticationProtocolResponder
                sessionManagerImpl.processInitiatorHandshake(sessionData, message.initiatorHandshakeMessage)?.let { responseMessage ->
                    val timestamp = Instant.now()
                    val newMetadata = InboundSessionMetadata(
                        source = responseMessage.header.sourceIdentity.toCorda(),
                        destination = responseMessage.header.destinationIdentity.toCorda(),
                        lastSendTimestamp = timestamp,
                        encryptionKeyId = "",
                        encryptionKeyTenant = "",
                        status = InboundSessionStatus.SentResponderHandshake,
                        expiry = Instant.now() + Duration.ofDays(7)
                    )
                    val session = sessionData.getSession()
                    val newState = State(
                        message.initiatorHandshakeMessage.header.sessionId,
                        SessionState(responseMessage, session)
                            .toAvro(schemaRegistry, sessionEncryptionOpsClient).toByteBuffer().array(),
                        metadata = newMetadata.toMetadata()
                    )
                    ProcessInitiatorHandshakeResult(responseMessage, newState, session)
                }
            }
            InboundSessionStatus.SentResponderHandshake -> {
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
                    ProcessInitiatorHandshakeResult(responderHandshakeToResend, newState, null)
                } else {
                    null
                }
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

    override val dominoTile = SimpleDominoTile(this::class.java.simpleName, coordinatorFactory)
}