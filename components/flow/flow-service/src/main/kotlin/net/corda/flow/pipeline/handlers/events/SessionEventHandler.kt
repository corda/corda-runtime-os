package net.corda.flow.pipeline.handlers.events

import net.corda.data.KeyValuePairList
import net.corda.data.flow.FlowInitiatorType
import net.corda.data.flow.FlowKey
import net.corda.data.flow.FlowStartContext
import net.corda.data.flow.event.SessionEvent
import net.corda.data.flow.event.session.SessionData
import net.corda.data.flow.event.session.SessionInit
import net.corda.data.flow.state.session.SessionState
import net.corda.data.flow.state.waiting.WaitingFor
import net.corda.flow.pipeline.CheckpointInitializer
import net.corda.flow.pipeline.events.FlowEventContext
import net.corda.flow.pipeline.exceptions.FlowEventException
import net.corda.flow.pipeline.exceptions.FlowFatalException
import net.corda.flow.pipeline.exceptions.FlowTransientException
import net.corda.flow.pipeline.handlers.waiting.WaitingForSessionInit
import net.corda.flow.pipeline.sandbox.FlowSandboxService
import net.corda.flow.pipeline.sessions.FlowSessionManager
import net.corda.flow.pipeline.sessions.protocol.FlowAndProtocolVersion
import net.corda.flow.utils.KeyValueStore
import net.corda.flow.utils.keyValuePairListOf
import net.corda.session.manager.Constants.Companion.FLOW_PROTOCOL
import net.corda.session.manager.Constants.Companion.FLOW_PROTOCOL_VERSIONS_SUPPORTED
import net.corda.session.manager.Constants.Companion.FLOW_PROTOCOL_VERSION_USED
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

        val now = Instant.now()
        val sessionId = sessionEvent.sessionId
        val updatedSessionState = sessionManager.processMessageReceived(
            sessionId,
            if (checkpoint.doesExist) checkpoint.getSessionState(sessionId) else null,
            sessionEvent,
            now
        )

        // Null is returned if duplicate [SessionInit]s are received
        val nextSessionEvent = sessionManager.getNextReceivedEvent(updatedSessionState)
        if (!checkpoint.doesExist) {
            val sessionInit = getSessionInitIfPresent(nextSessionEvent)
            if (nextSessionEvent == null || sessionInit == null) {
                discardSessionEvent(context, sessionEvent)
            } else {
                createInitiatedFlowCheckpoint(context, sessionInit.cpiId, nextSessionEvent, updatedSessionState)
            }
        }

        checkpoint.putSessionState(updatedSessionState)
        //do this last because the Holding Identity won't be available until after the checkpoint has been initiated
        context.flowMetrics.flowSessionMessageReceived(sessionEvent.payload::class.java.name)

        return context
    }

    private fun getSessionInitIfPresent(sessionEvent: SessionEvent?): SessionInit? {
        return when (val payload = sessionEvent?.payload) {
            is SessionInit -> payload
            is SessionData -> payload.sessionInit
            else -> null
        }
    }

    private fun getContextSessionProperties(protocolVersion: FlowAndProtocolVersion): KeyValuePairList {
        val sessionContext = KeyValueStore().apply {
            put(FLOW_PROTOCOL, protocolVersion.protocol)
            put(FLOW_PROTOCOL_VERSION_USED, protocolVersion.protocolVersion.toString())
        }

        return sessionContext.avro
    }

    private fun createInitiatedFlowCheckpoint(
        context: FlowEventContext<*>,
        cpiId: String,
        sessionEvent: SessionEvent,
        initialSessionState: SessionState,
    ) {
        val sessionId = sessionEvent.sessionId
        val (requestedProtocolName, initiatorVersionsSupported) = getProtocolInfo(sessionEvent.contextSessionProperties, sessionEvent)

        val initiatedFlowNameAndProtocolResult = initializeCheckpointAndGetResult(
            context, sessionEvent, cpiId, requestedProtocolName, initiatorVersionsSupported
        )

        //set initial session state, so it can be found when trying to send the confirmation message
        context.checkpoint.putSessionState(initialSessionState)
        context.flowMetrics.flowStarted()

        initiatedFlowNameAndProtocolResult.let { result ->
            when {
                result.isSuccess -> {
                    if (sessionEvent.payload is SessionInit) {
                        sendConfirmMessage(
                            result.getOrNull(),
                            requestedProtocolName,
                            initiatorVersionsSupported,
                            context,
                            sessionId
                        )
                    }
                }
                result.isFailure -> sendErrorMessage(
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
            WaitingFor(WaitingForSessionInit(sessionId)),
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

    private fun sendConfirmMessage(
        initiatedFlowNameAndProtocol: FlowAndProtocolVersion?,
        requestedProtocolName: String,
        initiatorVersionsSupported: List<Int>,
        context: FlowEventContext<*>,
        sessionId: String,
    ) {
        val flowAndProtocolVersion = initiatedFlowNameAndProtocol ?: throw FlowFatalException(
            "No responder is configured for protocol " +
                    "$requestedProtocolName at versions $initiatorVersionsSupported"
        )

        context.checkpoint.putSessionState(
            flowSessionManager.sendConfirmMessage(
                context.checkpoint,
                sessionId,
                getContextSessionProperties(flowAndProtocolVersion),
                Instant.now()
            )
        )
    }

    private fun sendErrorMessage(
        context: FlowEventContext<*>,
        sessionId: String,
        exception: Throwable
    ) {
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
