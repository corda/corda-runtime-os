package net.corda.flow.pipeline.handlers.events

import net.corda.data.flow.FlowInitiatorType
import net.corda.data.flow.FlowKey
import net.corda.data.flow.FlowStartContext
import net.corda.data.flow.event.SessionEvent
import net.corda.data.flow.event.session.SessionInit
import net.corda.data.flow.state.waiting.WaitingFor
import net.corda.flow.pipeline.CheckpointInitializer
import net.corda.flow.pipeline.events.FlowEventContext
import net.corda.flow.pipeline.exceptions.FlowEventException
import net.corda.flow.pipeline.exceptions.FlowTransientException
import net.corda.flow.pipeline.handlers.waiting.sessions.WaitingForSessionInit
import net.corda.flow.pipeline.sandbox.FlowSandboxService
import net.corda.flow.utils.emptyKeyValuePairList
import net.corda.session.manager.SessionManager
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
    private val checkpointInitializer: CheckpointInitializer
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

        val updatedSessionState = sessionManager.processMessageReceived(
            sessionEvent.sessionId,
            if (checkpoint.doesExist) checkpoint.getSessionState(sessionEvent.sessionId) else null,
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

    private fun createInitiatedFlowCheckpoint(
        context: FlowEventContext<*>,
        sessionInit: SessionInit,
        sessionEvent: SessionEvent
    ) {
        val sessionId = sessionEvent.sessionId
        val initiatingIdentity = sessionEvent.initiatingIdentity
        val initiatedIdentity = sessionEvent.initiatedIdentity
        val holdingIdentity = initiatedIdentity.toCorda()

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
                    "Failed to create the flow sandbox: ${e.message}",
                    e
                )
            }
            val initiatedFlow = protocolStore.responderForProtocol(sessionInit.protocol, sessionInit.versions, context)
            FlowStartContext.newBuilder()
                .setStatusKey(FlowKey(sessionId, initiatedIdentity))
                .setInitiatorType(FlowInitiatorType.P2P)
                .setRequestId(sessionId)
                .setIdentity(initiatedIdentity)
                .setCpiId(sessionInit.cpiId)
                .setInitiatedBy(initiatingIdentity)
                .setFlowClassName(initiatedFlow)
                .setContextPlatformProperties(emptyKeyValuePairList())
                .setCreatedTimestamp(Instant.now())
                .build()
        }
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
