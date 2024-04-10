package net.corda.p2p.linkmanager.sessions.messages

import net.corda.data.p2p.LinkInMessage
import net.corda.data.p2p.crypto.InitiatorHandshakeMessage
import net.corda.data.p2p.crypto.InitiatorHelloMessage
import net.corda.data.p2p.crypto.ResponderHandshakeMessage
import net.corda.data.p2p.crypto.ResponderHelloMessage
import net.corda.libs.statemanager.api.MetadataFilter
import net.corda.libs.statemanager.api.Operation
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.domino.logic.ComplexDominoTile
import net.corda.lifecycle.domino.logic.LifecycleWithDominoTile
import net.corda.p2p.crypto.protocol.api.AuthenticationProtocolInitiator
import net.corda.p2p.crypto.protocol.api.AuthenticationProtocolResponder
import net.corda.p2p.linkmanager.membership.calculateOutboundSessionKey
import net.corda.p2p.linkmanager.sessions.CreateAction
import net.corda.p2p.linkmanager.sessions.SessionManagerImpl
import net.corda.p2p.linkmanager.sessions.SessionManagerWarnings.alreadySessionWarning
import net.corda.p2p.linkmanager.sessions.SessionManagerWarnings.noSessionWarning
import net.corda.p2p.linkmanager.sessions.StateConvertor
import net.corda.p2p.linkmanager.sessions.StateFactory
import net.corda.p2p.linkmanager.sessions.StateManagerWrapper
import net.corda.p2p.linkmanager.sessions.UpdateAction
import net.corda.p2p.linkmanager.sessions.metadata.CommonMetadata
import net.corda.p2p.linkmanager.sessions.metadata.InboundSessionMetadata
import net.corda.p2p.linkmanager.sessions.metadata.InboundSessionMetadata.Companion.toInbound
import net.corda.p2p.linkmanager.sessions.metadata.InboundSessionStatus
import net.corda.p2p.linkmanager.sessions.metadata.OutboundSessionMetadata.Companion.toOutbound
import net.corda.p2p.linkmanager.sessions.metadata.OutboundSessionStatus
import net.corda.p2p.linkmanager.sessions.utils.InboundSessionMessage
import net.corda.p2p.linkmanager.sessions.utils.InboundSessionMessageContext
import net.corda.p2p.linkmanager.sessions.utils.OutboundSessionMessage
import net.corda.p2p.linkmanager.sessions.utils.OutboundSessionMessageContext
import net.corda.p2p.linkmanager.sessions.utils.Result
import net.corda.p2p.linkmanager.sessions.utils.SessionUtils.getSessionCounterpartiesFromState
import net.corda.p2p.linkmanager.sessions.utils.TraceableResult
import net.corda.p2p.linkmanager.state.SessionState
import net.corda.utilities.time.Clock
import net.corda.virtualnode.toCorda
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.time.Duration

@Suppress("LongParameterList")
internal class SessionMessageProcessor(
    coordinatorFactory: LifecycleCoordinatorFactory,
    private val clock: Clock,
    private val stateManager: StateManagerWrapper,
    private val sessionManagerImpl: SessionManagerImpl,
    private val stateConvertor: StateConvertor,
    private val stateFactory: StateFactory = StateFactory(stateConvertor),
) : LifecycleWithDominoTile {
    companion object {
        private val SESSION_VALIDITY_PERIOD: Duration = Duration.ofDays(7)
        private val logger: Logger = LoggerFactory.getLogger(this::class.java.enclosingClass)
    }

    fun <T> processOutboundSessionMessages(messages: List<Pair<T, LinkInMessage?>>): Collection<TraceableResult<T>> {
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

    fun <T> processInboundSessionMessages(messages: List<Pair<T, LinkInMessage?>>): Collection<TraceableResult<T>> {
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
                    val newState = stateFactory.createState(
                        key = message.initiatorHelloMessage.header.sessionId,
                        sessionState = SessionState(responseMessage, authenticationProtocol),
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
                    val newState = stateFactory.createState(state.managerState, updatedMetadata.toMetadata())
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
                    val newState = stateFactory.createState(
                        key = message.initiatorHandshakeMessage.header.sessionId,
                        sessionState = SessionState(responseMessage, session),
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
                    val newState = stateFactory.createState(state.managerState, updatedMetadata.toMetadata())
                    Result(responderHandshakeToResend, UpdateAction(newState, true), null)
                } else {
                    null
                }
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
                val counterparties = getSessionCounterpartiesFromState(state.managerState)

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
                    val newState = stateFactory.createState(
                        key = calculateOutboundSessionKey(
                            counterparties.ourId,
                            counterparties.counterpartyId,
                            counterparties.serial,
                        ),
                        sessionState = SessionState(responseMessage, authenticationProtocol),
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
                    val newState = stateFactory.createState(state.managerState, updatedMetadata.toMetadata())
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

    private fun processResponderHandshake(
        state: StateManagerWrapper.StateManagerSessionState?,
        message: OutboundSessionMessage.ResponderHandshakeMessage,
    ): Result? {
        val metadata = state?.managerState?.metadata?.toOutbound()
        return when (metadata?.status) {
            OutboundSessionStatus.SentInitiatorHandshake -> {
                val sessionState = state.sessionState.sessionData as? AuthenticationProtocolInitiator ?: return null
                val counterparties = getSessionCounterpartiesFromState(state.managerState)

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
                    val newState = stateFactory.createState(
                        key = calculateOutboundSessionKey(
                            counterparties.ourId,
                            counterparties.counterpartyId,
                            counterparties.serial,
                        ),
                        sessionState = SessionState(null, session),
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

    private fun getSessionIdFilter(sessionId: String): MetadataFilter = MetadataFilter("sessionId", Operation.Equals, sessionId)

    private fun <T> Any.getSessionIdIfOutboundSessionMessage(trace: T): OutboundSessionMessageContext<T>? {
        return when (this) {
            is ResponderHelloMessage ->
                this.header?.sessionId?.let { sessionId ->
                    OutboundSessionMessageContext(
                        sessionId,
                        OutboundSessionMessage.ResponderHelloMessage(this),
                        trace,
                    )
                }

            is ResponderHandshakeMessage ->
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

    private fun <T> Any.getSessionIdIfInboundSessionMessage(trace: T): InboundSessionMessageContext<T>? {
        return when (this) {
            is InitiatorHelloMessage ->
                this.header?.sessionId?.let { sessionId ->
                    InboundSessionMessageContext(
                        sessionId,
                        InboundSessionMessage.InitiatorHelloMessage(this),
                        trace,
                    )
                }
            is InitiatorHandshakeMessage ->
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

    override val dominoTile =
        ComplexDominoTile(
            this::class.java.simpleName,
            coordinatorFactory,
            dependentChildren =
            setOf(
                stateManager.name,
                sessionManagerImpl.dominoTile.coordinatorName,
            ),
            managedChildren = emptySet(),
        )
}