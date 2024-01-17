package net.corda.p2p.linkmanager.sessions

import com.github.benmanes.caffeine.cache.Cache
import com.github.benmanes.caffeine.cache.Caffeine
import net.corda.cache.caffeine.CacheFactoryImpl
import net.corda.crypto.client.SessionEncryptionOpsClient
import net.corda.data.p2p.AuthenticatedMessageAndKey
import net.corda.data.p2p.LinkInMessage
import net.corda.data.p2p.LinkOutMessage
import net.corda.libs.statemanager.api.State
import net.corda.libs.statemanager.api.StateManager
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.LifecycleCoordinatorName
import net.corda.lifecycle.domino.logic.ComplexDominoTile
import net.corda.p2p.crypto.protocol.api.AuthenticationProtocolResponder
import net.corda.p2p.crypto.protocol.api.Session
import net.corda.p2p.linkmanager.sessions.metadata.InboundSessionMetadata
import net.corda.p2p.linkmanager.sessions.metadata.InboundSessionMetadata.Companion.from
import net.corda.p2p.linkmanager.sessions.metadata.InboundSessionStatus
import net.corda.p2p.linkmanager.state.SessionState
import net.corda.utilities.time.Clock
import net.corda.virtualnode.HoldingIdentity
import net.corda.virtualnode.toCorda
import org.slf4j.LoggerFactory
import java.time.Duration
import net.corda.data.p2p.crypto.InitiatorHandshakeMessage as AvroInitiatorHandshakeMessage
import net.corda.data.p2p.crypto.InitiatorHelloMessage as AvroInitiatorHelloMessage

internal class StatefulSessionManagerImpl(
    coordinatorFactory: LifecycleCoordinatorFactory,
    private val stateManager: StateManager,
    private val sessionManagerImpl: SessionManagerImpl,
    private val stateConvertor: StateConvertor,
    private val clock: Clock,
) : SessionManager {

    private companion object {
        const val CACHE_SIZE = 10_000L
        val SESSION_VALIDITY_PERIOD = Duration.ofDays(7)
        val logger = LoggerFactory.getLogger(StatefulSessionManagerImpl::class.java)
    }

    override fun <T> processOutboundMessages(
        wrappedMessages: Collection<T>,
        getMessage: (T) -> AuthenticatedMessageAndKey,
    ): Collection<Pair<T, SessionManager.SessionState>> {
        return emptyList()
    }

    override fun <T> getSessionsById(
        uuids: Collection<T>,
        getSessionId: (T) -> String,
    ): Collection<Pair<T, SessionManager.SessionDirection>> {
        val traceable = uuids.associateBy { getSessionId(it) }
        val sessionFromCache = cachedInboundSessions.getAllPresent(traceable.keys)
        val sessionsIdsNotInCache = traceable - sessionFromCache.keys
        val inboundSessionsFromStateManager: List<Pair<T, SessionManager.SessionDirection>> =
            if (sessionsIdsNotInCache.isEmpty()) {
                emptyList()
            } else {
                stateManager.get(sessionsIdsNotInCache.keys).entries.mapNotNull { (sessionId, state) ->
                    val session = stateConvertor.toCordaSessionState(
                        state,
                        sessionManagerImpl.revocationCheckerClient::checkRevocation,
                    ).sessionData as? Session
                    session?.let {
                        sessionsIdsNotInCache[sessionId]?.let {
                            val inboundSession = SessionManager.SessionDirection.Inbound(state.metadata.from().toCounterparties(), session)
                            cachedInboundSessions.put(sessionId, inboundSession)
                            it to inboundSession
                        }
                    }
                }
            }
        // In CORE-18630 check cache/state manager for outbound sessions if not found for inbound sessions.
        // We should avoid as unnecessary reads of the state manager, so first check the inbound and outbound session cache,
        // then the state manager for an inbound session (query with session ID), then the state manager for an outbound session
        // (query for sessionId in metadata).

        return sessionFromCache.mapNotNull { (sessionId, sessionDirection) ->
            traceable[sessionId]?.let { it to sessionDirection }
        } + inboundSessionsFromStateManager
    }

    override fun <T> processSessionMessages(
        wrappedMessages: Collection<T>,
        getMessage: (T) -> LinkInMessage,
    ): Collection<Pair<T, LinkOutMessage?>> {
        val messages = wrappedMessages.map { it to getMessage(it) }
        return processInboundSessionMessages(messages)
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

    private sealed interface InboundSessionMessage {
        data class InitiatorHelloMessage(
            val initiatorHelloMessage: AvroInitiatorHelloMessage,
        ) : InboundSessionMessage
        data class InitiatorHandshakeMessage(
            val initiatorHandshakeMessage: AvroInitiatorHandshakeMessage,
        ) : InboundSessionMessage
    }

    private data class TraceableResult<T>(
        val traceable: T,
        val result: Result?,
    )
    private data class Result(
        val message: LinkOutMessage,
        val stateUpdate: State,
        val sessionToCache: Session?,
    )
    private val cachedInboundSessions: Cache<String, SessionManager.SessionDirection> = CacheFactoryImpl().build(
        "P2P-Sessions-cache",
        Caffeine.newBuilder()
            .maximumSize(CACHE_SIZE),
    )

    private fun InboundSessionMetadata.toCounterparties(): SessionManager.Counterparties {
        return SessionManager.Counterparties(
            ourId = this.destination,
            counterpartyId = this.source,
        )
    }

    private fun <T> processInboundSessionMessages(messages: List<Pair<T, LinkInMessage?>>): Collection<Pair<T, LinkOutMessage?>> {
        val messageContexts = messages.mapNotNull {
            it.second?.payload?.getSessionIdIfInboundSessionMessage(it.first)
        }
        if (messageContexts.isEmpty()) {
            return emptyList()
        }
        val states = stateManager.get(messageContexts.map { it.sessionId })
        val results = messageContexts.map {
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
            it.sessionId to TraceableResult(it.trace, result)
        }.toMap()
        val failedUpdate = stateManager.update(results.values.mapNotNull { it.result?.stateUpdate })
            .keys.onEach {
                logger.warn("Failed to update the state of session $it")
            }

        return results.mapNotNull { (sessionId, result) ->
            if (failedUpdate.contains(sessionId)) {
                null
            } else {
                result
            }
        }.onEach { result ->
            result.result?.sessionToCache?.let { sessionToCache ->
                cachedInboundSessions.put(
                    sessionToCache.sessionId,
                    SessionManager.SessionDirection.Inbound(
                        result.result.stateUpdate.metadata.from().toCounterparties(),
                        sessionToCache,
                    ),
                )
            }
        }.map { result ->
            result.traceable to result.result?.message
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
        val metadata = state?.metadata?.from()
        return when (metadata?.status) {
            null -> {
                sessionManagerImpl.processInitiatorHello(message.initiatorHelloMessage)?.let {
                        (responseMessage, authenticationProtocol) ->
                    val timestamp = clock.instant()
                    val newMetadata = InboundSessionMetadata(
                        source = responseMessage.header.destinationIdentity.toCorda(),
                        destination = responseMessage.header.sourceIdentity.toCorda(),
                        lastSendTimestamp = timestamp,
                        status = InboundSessionStatus.SentResponderHello,
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
            InboundSessionStatus.SentResponderHello -> {
                if (metadata.lastSendExpired(clock)) {
                    val timestamp = clock.instant()
                    val updatedMetadata = metadata.copy(lastSendTimestamp = timestamp)
                    val responderHelloToResend = stateConvertor.toCordaSessionState(
                        state,
                        sessionManagerImpl.revocationCheckerClient::checkRevocation,
                    )
                        .message
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
            InboundSessionStatus.SentResponderHandshake -> {
                null
            }
        }
    }

    private data class ProcessInitiatorHandshakeResult(
        val responseMessage: LinkOutMessage,
        val stateToUpdate: State,
        val session: Session?,
    )

    private fun processInitiatorHandshake(
        state: State?,
        message: InboundSessionMessage.InitiatorHandshakeMessage,
    ): ProcessInitiatorHandshakeResult? {
        val metadata = state?.metadata?.from()
        return when (metadata?.status) {
            null -> {
                null
            }
            InboundSessionStatus.SentResponderHello -> {
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
                        status = InboundSessionStatus.SentResponderHandshake,
                        expiry = timestamp + SESSION_VALIDITY_PERIOD,
                    )
                    val session = sessionData.getSession()
                    val newState = State(
                        message.initiatorHandshakeMessage.header.sessionId,
                        stateConvertor.toStateByteArray(SessionState(responseMessage, session)),
                        version = state.version + 1,
                        metadata = newMetadata.toMetadata(),
                    )
                    ProcessInitiatorHandshakeResult(responseMessage, newState, session)
                }
            }
            InboundSessionStatus.SentResponderHandshake -> {
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
                    ProcessInitiatorHandshakeResult(responderHandshakeToResend, newState, null)
                } else {
                    null
                }
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
