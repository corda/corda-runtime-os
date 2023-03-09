package net.corda.flow.pipeline.handlers.events

import java.time.Instant
import net.corda.data.KeyValuePairList
import net.corda.data.flow.FlowInitiatorType
import net.corda.data.flow.FlowKey
import net.corda.data.flow.FlowStartContext
import net.corda.data.flow.event.SessionEvent
import net.corda.data.flow.event.session.SessionInit
import net.corda.data.flow.state.waiting.WaitingFor
import net.corda.flow.pipeline.CheckpointInitializer
import net.corda.flow.pipeline.events.FlowEventContext
import net.corda.flow.pipeline.exceptions.FlowEventException
import net.corda.flow.pipeline.exceptions.FlowFatalException
import net.corda.flow.pipeline.exceptions.FlowTransientException
import net.corda.flow.pipeline.handlers.waiting.sessions.WaitingForSessionInit
import net.corda.flow.pipeline.sandbox.FlowSandboxService
import net.corda.flow.pipeline.sessions.FlowSessionManager
import net.corda.flow.pipeline.sessions.protocol.FlowAndProtocolVersion
import net.corda.flow.utils.KeyValueStore
import net.corda.flow.utils.emptyKeyValuePairList
import net.corda.session.manager.Constants.Companion.FLOW_PROTOCOL
import net.corda.session.manager.Constants.Companion.FLOW_PROTOCOL_VERSIONS_SUPPORTED
import net.corda.session.manager.Constants.Companion.FLOW_PROTOCOL_VERSION_USED
import net.corda.session.manager.SessionManager
import net.corda.utilities.debug
import net.corda.utilities.trace
import net.corda.virtualnode.toCorda
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import org.slf4j.LoggerFactory

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
        val nextSessionPayload = nextSessionEvent?.payload

        if (!checkpoint.doesExist) {
            if (nextSessionPayload is SessionInit) {
                createInitiatedFlowCheckpoint(context, nextSessionPayload, nextSessionEvent)
            } else {
                discardSessionEvent(context, sessionEvent)
            }
        }

        checkpoint.putSessionState(updatedSessionState)

        return context
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
        sessionInit: SessionInit,
        sessionEvent: SessionEvent,
    ) {
        val sessionId = sessionEvent.sessionId
        val initiatingIdentity = sessionEvent.initiatingIdentity
        val initiatedIdentity = sessionEvent.initiatedIdentity
        val holdingIdentity = initiatedIdentity.toCorda()
        val sessionProperties = sessionInit.contextSessionProperties
        val requestedProtocolName = sessionProperties.get(FLOW_PROTOCOL).toString()
        val initiatorVersionsSupported = sessionProperties.get(FLOW_PROTOCOL_VERSIONS_SUPPORTED).toString().split(",").map { it.toInt() }

        var initiatedFlowNameAndProtocol: FlowAndProtocolVersion? = null
        checkpointInitializer.initialize(
            context.checkpoint,
            WaitingFor(WaitingForSessionInit(sessionId)),
            holdingIdentity
        ) {
            val protocolStore = try {
                flowSandboxService.get(holdingIdentity, it).protocolStore
            } catch (e: Exception) {
                // We assume that all sandbox creation failures are transient. This likely isn't true, but to handle
                // it properly will need some changes to the exception handling to get the context elsewhere. Transient here
                // will get the right failure eventually, so this is fine for now.
                throw FlowTransientException(
                    "Failed to create the flow sandbox: ${e.message ?: "No exception message provided."}",
                    e
                )
            }
            val flowAndProtocolVersion = protocolStore.responderForProtocol(requestedProtocolName, initiatorVersionsSupported, context)
            initiatedFlowNameAndProtocol = flowAndProtocolVersion
            FlowStartContext.newBuilder()
                .setStatusKey(FlowKey(sessionId, initiatedIdentity))
                .setInitiatorType(FlowInitiatorType.P2P)
                .setRequestId(sessionId)
                .setIdentity(initiatedIdentity)
                .setCpiId(sessionInit.cpiId)
                .setInitiatedBy(initiatingIdentity)
                .setFlowClassName(flowAndProtocolVersion.flowClassName)
                .setContextPlatformProperties(emptyKeyValuePairList())
                .setCreatedTimestamp(Instant.now())
                .build()
        }

        sendConfirmMessage(initiatedFlowNameAndProtocol, requestedProtocolName, initiatorVersionsSupported, context, sessionId)
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
