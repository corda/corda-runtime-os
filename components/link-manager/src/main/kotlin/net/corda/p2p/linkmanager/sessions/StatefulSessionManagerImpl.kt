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
import net.corda.p2p.crypto.protocol.api.SerialisableSessionData
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
    val stateManager: StateManager,
    val sessionManagerImpl: SessionManagerImpl,
    val schemaRegistry: AvroSchemaRegistry,
    val sessionEncryptionOpsClient: SessionEncryptionOpsClient,
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
        val sessionFromCache = uuids.map { it to sessionCache[getSessionId(it)] }
        val sessionsIdsNotInCache = sessionFromCache.filter { it.second == null }.map { getSessionId(it.first) }
        val sessionsFromStateManager = stateManager.get(sessionsIdsNotInCache)
        sessionsFromStateManager.entries.forEach { (sessionId, state) ->
            sessionCache[sessionId] = AvroSessionState.fromByteBuffer(ByteBuffer.wrap(state.value))
                .toCorda(schemaRegistry, sessionEncryptionOpsClient, checkRevocation).sessionData as Session
        }
        val outboundSessions = sessionFromCache.map { it.first to it.second }

        return emptyList() //To be implemented in CORE-18630 (getting the outbound sessions) + CORE-18631 (getting the inbound sessions).
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
        val stateUpdate: State
        val sessionToCache: Session?
    )

    private val sessionCache = ConcurrentHashMap<String, Session>()

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
                sessionCache.put(sessionToCache.sessionId, sessionToCache)
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
                    val responderHelloToResend = AvroSessionState.fromByteBuffer(ByteBuffer.wrap(state.value)).message
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
                val session = schemaRegistry.deserialize(
                    ByteBuffer.wrap(state.value),
                    AvroSessionState::class.java,
                    null
                ).encryptedSessionData as AuthenticationProtocolResponder
                sessionManagerImpl.processInitiatorHandshake(session, message.initiatorHandshakeMessage)?.let { responseMessage ->
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
                    val session = session.getSession()
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
                        .toCorda(schemaRegistry, encryptionClient, checkRevocation).sessionData
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
            is InitiatorHelloMessage -> InboundSessionMessageContext(this.header!!.sessionId, InboundSessionMessage.InitiatorHelloMessage(this), trace)
            is InitiatorHandshakeMessage -> InboundSessionMessageContext(this.header!!.sessionId, InboundSessionMessage.InitiatorHandshakeMessage(this), trace)
            else -> null
        }
    }

    override val dominoTile = SimpleDominoTile(this::class.java.simpleName, coordinatorFactory)
}