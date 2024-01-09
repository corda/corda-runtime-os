package net.corda.p2p.linkmanager.sessions

import java.nio.ByteBuffer
import java.time.Duration
import java.time.Instant
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
import net.corda.p2p.crypto.protocol.api.Session
import net.corda.p2p.linkmanager.sessions.metadata.InboundSessionMetadata
import net.corda.p2p.linkmanager.sessions.metadata.InboundSessionStatus
import net.corda.p2p.linkmanager.state.SessionState
import net.corda.schema.registry.AvroSchemaRegistry
import net.corda.virtualnode.HoldingIdentity
import net.corda.virtualnode.toCorda
import org.slf4j.LoggerFactory

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

    private data class InboundSessionMessageContext(
        val sessionId: String,
        val inboundSessionMessage: InboundSessionMessage
    )

    private sealed class InboundSessionMessage {
        data class InitiatorHelloMessage(
            val initiatorHelloMessage: net.corda.data.p2p.crypto.InitiatorHelloMessage
        ): InboundSessionMessage()
        data class InitiatorHandshakeMessage(
            val initiatorHandshakeMessage: net.corda.data.p2p.crypto.InitiatorHandshakeMessage
        ): InboundSessionMessage()
    }

    private fun <T> processInboundSessionMessages(messages: List<Pair<T, LinkInMessage?>>): Collection<Pair<T, LinkOutMessage?>> {
        val messageContexts = messages.mapNotNull {
            it.second?.payload?.getSessionIdIfInboundSessionMessage()
        }
        val states = stateManager.get(messageContexts.map { it.sessionId })
        messageContexts.map {
            val state = states[it.sessionId]
            when (it.inboundSessionMessage) {
                is InboundSessionMessage.InitiatorHelloMessage -> {
                    processInitiatorHello(state, it.inboundSessionMessage)
                }
                is InboundSessionMessage.InitiatorHandshakeMessage -> {
                    processInitiatorHandshake(state, it)
                }
            }
        }

        return emptyList()
    }

    /**
     * TODO Refactor SessionManagerImpl to move logic needed here i.e. create an ResponderHello from an InitiatorHello
     * into a new component. This component should not store the AuthenticationProtocol in an in memory map or replay session
     * messages.
     */
    private fun processInitiatorHello(
        state: State?,
        messageContext: InboundSessionMessage.InitiatorHelloMessage,
    ): Pair<LinkOutMessage?, State>? {
        val metadata = state?.metadata?.let { metadataMap -> InboundSessionMetadata(metadataMap) }
        when (metadata?.status) {
            null -> {
                sessionManagerImpl.processInitiatorHello(messageContext.initiatorHelloMessage)?.let {
                    (message, authenticationProtocol) ->
                    val timestamp = Instant.now()
                    val newMetadata = InboundSessionMetadata(
                        source = message.header.destinationIdentity.toCorda(),
                        destination = message.header.sourceIdentity.toCorda(),
                        lastSendTimestamp = timestamp,
                        encryptionKeyId = "",
                        encryptionKeyTenant = "",
                        status = InboundSessionStatus.SentResponderHello,
                        expiry = Instant.now() + Duration.ofDays(7)
                    )
                    val newState = State(
                        messageContext.initiatorHelloMessage.header.sessionId,
                        SessionState(message, authenticationProtocol)
                            .toAvro(schemaRegistry, sessionEncryptionOpsClient).toByteBuffer().array(),
                        metadata = newMetadata.toMetadata()
                    )
                    return message to newState
                } ?: return null
            }
            InboundSessionStatus.SentResponderHello -> {
                if (metadata.lastSendExpired()) {
                    val timestamp = Instant.now()
                    val updatedMetadata = metadata.copy(lastSendTimestamp = timestamp)
                    val responderHelloToResend = net.corda.data.p2p.state.SessionState.fromByteBuffer(ByteBuffer.wrap(state.value)).message
                    val newState = State(
                        key = state.key,
                        value = state.value,
                        version = state.version,
                        metadata = updatedMetadata.toMetadata()
                    )
                    return responderHelloToResend to newState
                } else {
                    return null
                }
            }
            InboundSessionStatus.SentResponderHandshake -> {
                return null
            }
        }
    }

    private fun processInitiatorHandshake(state: State?, messageContext: InboundSessionMessageContext): Pair<LinkOutMessage?, State>?{
        val metadata = state?.metadata?.let { metadataMap -> InboundSessionMetadata(metadataMap) }
        when (metadata?.status) {
            null -> {

            }
            InboundSessionStatus.SentResponderHello -> {

            }
            InboundSessionStatus.SentResponderHandshake -> {

            }
        }
    }

    private fun Any.getSessionIdIfInboundSessionMessage(): InboundSessionMessageContext? {
        return when (this) {
            is InitiatorHelloMessage -> InboundSessionMessageContext(this.header!!.sessionId, InboundSessionMessage.InitiatorHelloMessage(this))
            is InitiatorHandshakeMessage -> InboundSessionMessageContext(this.header!!.sessionId, InboundSessionMessage.InitiatorHandshakeMessage(this))
            else -> null
        }
    }

    override val dominoTile = SimpleDominoTile(this::class.java.simpleName, coordinatorFactory)
}