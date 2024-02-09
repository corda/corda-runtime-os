package net.corda.flow.pipeline.handlers.events

import net.corda.data.KeyValuePairList
import net.corda.data.flow.FlowInitiatorType
import net.corda.data.flow.FlowKey
import net.corda.data.flow.FlowStartContext
import net.corda.data.flow.event.SessionEvent
import net.corda.data.flow.event.session.SessionCounterpartyInfoRequest
import net.corda.data.flow.event.session.SessionData
import net.corda.data.flow.event.session.SessionInit
import net.corda.data.flow.state.session.SessionStateType
import net.corda.data.flow.state.waiting.WaitingFor
import net.corda.data.flow.state.waiting.WaitingForStartFlow
import net.corda.flow.pipeline.CheckpointInitializer
import net.corda.flow.pipeline.events.FlowEventContext
import net.corda.flow.pipeline.exceptions.FlowEventException
import net.corda.flow.pipeline.exceptions.FlowFatalException
import net.corda.flow.pipeline.exceptions.FlowTransientException
import net.corda.flow.pipeline.sandbox.FlowSandboxService
import net.corda.flow.pipeline.sessions.FlowSessionManager
import net.corda.flow.pipeline.sessions.protocol.FlowAndProtocolVersion
import net.corda.flow.state.FlowCheckpoint
import net.corda.flow.utils.KeyValueStore
import net.corda.flow.utils.emptyKeyValuePairList
import net.corda.flow.utils.keyValuePairListOf
import net.corda.flow.utils.toMap
import net.corda.session.manager.Constants.Companion.FLOW_PROTOCOL
import net.corda.session.manager.Constants.Companion.FLOW_PROTOCOL_VERSIONS_SUPPORTED
import net.corda.session.manager.Constants.Companion.FLOW_PROTOCOL_VERSION_USED
import net.corda.session.manager.Constants.Companion.FLOW_SESSION_REQUIRE_CLOSE
import net.corda.session.manager.Constants.Companion.FLOW_SESSION_TIMEOUT_MS
import net.corda.session.manager.SessionManager
import net.corda.utilities.MDC_CLIENT_ID
import net.corda.utilities.debug
import net.corda.utilities.trace
import net.corda.virtualnode.toCorda
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import org.slf4j.LoggerFactory
import java.time.Instant

@Component(service = [FlowEventHandler::class])
class SessionEventHandler @Activate constructor(
    @Reference(service = FlowSandboxService::class)
    private val flowSandboxService: FlowSandboxService,
    @Reference(service = SessionManager::class)
    private val sessionManager: SessionManager,
    @Reference(service = CheckpointInitializer::class)
    private val checkpointInitializer: CheckpointInitializer,
    @Reference(service = FlowSessionManager::class)
    private val flowSessionManager: FlowSessionManager,
) : FlowEventHandler<SessionEvent> {

    private companion object {
        val log = LoggerFactory.getLogger(this::class.java.enclosingClass)
    }

    override val type = SessionEvent::class.java

    override fun preProcess(context: FlowEventContext<SessionEvent>): FlowEventContext<SessionEvent> {
        val checkpoint = context.checkpoint
        val sessionEvent = context.inputEventPayload
        log.trace { "Session event in handler: ${sessionEvent.payload}" }

        createCheckpointIfDoesNotExist(checkpoint, sessionEvent, context)
        processSessionEvent(sessionEvent, checkpoint)
        // Metrics require the holding identity to be set before use, as they are tagged by holding ID.
        context.flowMetrics.flowSessionMessageReceived(sessionEvent.payload::class.java.name)

        return context
    }

    private fun processSessionEvent(sessionEvent: SessionEvent, checkpoint: FlowCheckpoint) {
        val now = Instant.now()
        val sessionId = sessionEvent.sessionId
        val updatedSessionState = sessionManager.processMessageReceived(
            sessionId,
            if (checkpoint.doesExist) checkpoint.getSessionState(sessionId) else null,
            sessionEvent,
            now
        )
        checkpoint.putSessionState(updatedSessionState)
    }

    /**
     * If a checkpoint does not exist and the session event has a payload containing init information then create the checkpoint
     */
    private fun createCheckpointIfDoesNotExist(
        checkpoint: FlowCheckpoint,
        sessionEvent: SessionEvent,
        context: FlowEventContext<SessionEvent>
    ) {
        if (!checkpoint.doesExist) {
            val sessionInit = getSessionInitIfPresent(sessionEvent)
            if (sessionInit == null) {
                discardSessionEvent(context, sessionEvent)
            } else {
                createInitiatedFlowCheckpoint(context, sessionInit.cpiId, sessionEvent)
            }
        }
    }

    /**
     * Extract the SessionInit object from the given SessionEvent
     */
    private fun getSessionInitIfPresent(sessionEvent: SessionEvent): SessionInit? {
        return when (val payload = sessionEvent.payload) {
            is SessionCounterpartyInfoRequest -> payload.sessionInit
            is SessionData -> payload.sessionInit
            else -> null
        }
    }

    /**
     * Get the context session properties for this session based on the counterparties sent session properties and the calculated flow
     * protocol and version to be used.
     */
    private fun getContextSessionProperties(counterpartySessionProperties: KeyValuePairList, protocolVersion: FlowAndProtocolVersion):
            KeyValuePairList {
        val counterpartySessionPropertiesMap = counterpartySessionProperties.toMap()
        val requireClose = counterpartySessionPropertiesMap[FLOW_SESSION_REQUIRE_CLOSE] ?: throw FlowFatalException("RequireClose was not" +
                " set in the session properties")
        val sessionTimeoutMs = counterpartySessionPropertiesMap[FLOW_SESSION_TIMEOUT_MS]
        val sessionContext = KeyValueStore().apply {
            put(FLOW_PROTOCOL, protocolVersion.protocol)
            put(FLOW_PROTOCOL_VERSION_USED, protocolVersion.protocolVersion.toString())
            put(FLOW_SESSION_REQUIRE_CLOSE, requireClose)
            if (sessionTimeoutMs != null) {
                put(FLOW_SESSION_TIMEOUT_MS, sessionTimeoutMs)
            }
        }

        return sessionContext.avro
    }

    private fun createInitiatedFlowCheckpoint(
        context: FlowEventContext<*>,
        cpiId: String,
        sessionEvent: SessionEvent
    ) {
        val sessionId = sessionEvent.sessionId
        val (requestedProtocolName, initiatorVersionsSupported) = getProtocolInfo(sessionEvent.contextSessionProperties, sessionEvent)
        val initiatedFlowNameAndProtocolResult = initializeCheckpointAndGetResult(
            context, sessionEvent, cpiId, requestedProtocolName, initiatorVersionsSupported
        )

        //set initial session state, so it can be found when trying to send the confirmation message
        context.flowMetrics.flowStarted()

        initiatedFlowNameAndProtocolResult.let { result ->
            if (result.isSuccess) {
                context.checkpoint.putSessionState(sessionManager.generateSessionState(
                    sessionId,
                    getContextSessionProperties(sessionEvent.contextSessionProperties, result.getOrThrow()),
                    sessionEvent.initiatingIdentity,
                    Instant.now(),
                    SessionStateType.CONFIRMED
                ))
            } else {
                sendErrorMessage(
                    context,
                    sessionId,
                    initiatedFlowNameAndProtocolResult.exceptionOrNull() ?:
                    FlowFatalException("Failed to create initiated checkpoint for session: $sessionId.")
                )
            }
        }
    }

    private fun initializeCheckpointAndGetResult(
        context: FlowEventContext<*>,
        sessionEvent: SessionEvent,
        cpiId: String,
        requestedProtocolName: String,
        initiatorVersionsSupported: List<Int>
    ): Result<FlowAndProtocolVersion> {
        val sessionId = sessionEvent.sessionId
        val initiatingIdentity = sessionEvent.initiatingIdentity
        val initiatedIdentity = sessionEvent.initiatedIdentity
        val holdingIdentity = initiatedIdentity.toCorda()
        var initiatedFlowNameAndProtocolResult: Result<FlowAndProtocolVersion>? = null

        checkpointInitializer.initialize(
            context.checkpoint,
            WaitingFor(WaitingForStartFlow()),
            holdingIdentity
        ) {
            val protocolStore = try {
                flowSandboxService.get(holdingIdentity, it).protocolStore
            } catch (e: Exception) {
                throw FlowTransientException(
                    "Failed to create the flow sandbox: ${e.message ?: "No exception message provided."}",
                    e
                )
            }

            initiatedFlowNameAndProtocolResult = runCatching {
                protocolStore.responderForProtocol(requestedProtocolName, initiatorVersionsSupported, context)
            }

            FlowStartContext.newBuilder()
                .setStatusKey(FlowKey(sessionId, initiatedIdentity))
                .setInitiatorType(FlowInitiatorType.P2P)
                .setRequestId(sessionId)
                .setIdentity(initiatedIdentity)
                .setCpiId(cpiId)
                .setInitiatedBy(initiatingIdentity)
                .setFlowClassName(initiatedFlowNameAndProtocolResult?.getOrNull()?.flowClassName ?: "Invalid protocol")
                .setContextPlatformProperties(keyValuePairListOf(mapOf(MDC_CLIENT_ID to sessionId)))
                .setCreatedTimestamp(Instant.now())
                .build()
        }

        return initiatedFlowNameAndProtocolResult!!
    }


    private fun getProtocolInfo(
        contextSessionProperties: KeyValuePairList,
        sessionEvent: SessionEvent,
    ): Pair<String, List<Int>> {
        val sessionProperties = KeyValueStore(contextSessionProperties)
        val requestedProtocolName = sessionProperties[FLOW_PROTOCOL]
        val initiatorVersionsSupportedProp = sessionProperties[FLOW_PROTOCOL_VERSIONS_SUPPORTED]
        if (requestedProtocolName == null || initiatorVersionsSupportedProp == null) {
            throw FlowFatalException(
                "Failed to start initiated flow for sessionId ${sessionEvent.sessionId}. Flow protocol info is " +
                        "missing from SessionInit"
            )
        }
        return Pair(requestedProtocolName, initiatorVersionsSupportedProp.split(",").map { it.trim().toInt() })
    }

    private fun sendErrorMessage(
        context: FlowEventContext<*>,
        sessionId: String,
        exception: Throwable
    ) {
        context.checkpoint.putSessionState(sessionManager.generateSessionState(
            sessionId,
            emptyKeyValuePairList(),
            (context.inputEventPayload as SessionEvent).initiatingIdentity,
            Instant.now(),
            SessionStateType.ERROR
        ))

        context.checkpoint.putSessionStates(
            flowSessionManager.sendErrorMessages(
                context.checkpoint,
                listOf(sessionId),
                exception,
                Instant.now()
            )
        )
    }

    private fun discardSessionEvent(context: FlowEventContext<SessionEvent>, sessionEvent: SessionEvent) {
        log.debug {
            "Received a ${sessionEvent.payload::class.simpleName} for flow [${context.inputEvent.flowId}] that does not exist. " +
                    "The event will be discarded. ${SessionEvent::class.simpleName}: $sessionEvent"
        }
        throw FlowEventException(
            "SessionEventHandler received a ${context.inputEventPayload.payload::class.simpleName} for flow" +
                    " [${context.inputEvent.flowId}] that does not exist"
        )
    }
}
