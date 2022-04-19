package net.corda.flow.pipeline.handlers.events

import net.corda.data.flow.FlowInitiatorType
import net.corda.data.flow.FlowKey
import net.corda.data.flow.FlowStartContext
import net.corda.data.flow.event.SessionEvent
import net.corda.data.flow.event.session.SessionInit
import net.corda.data.flow.state.waiting.WaitingFor
import net.corda.flow.pipeline.FlowEventContext
import net.corda.flow.pipeline.FlowProcessingException
import net.corda.flow.pipeline.handlers.waiting.sessions.WaitingForSessionInit
import net.corda.flow.pipeline.sandbox.FlowSandboxContextTypes
import net.corda.flow.pipeline.sandbox.FlowSandboxService
import net.corda.sandboxgroupcontext.getObjectByKey
import net.corda.session.manager.SessionManager
import net.corda.v5.base.util.contextLogger
import net.corda.virtualnode.HoldingIdentity
import net.corda.virtualnode.toCorda
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import java.time.Instant

@Component(service = [FlowEventHandler::class])
class SessionEventHandler @Activate constructor(
    @Reference(service = FlowSandboxService::class)
    private val flowSandboxService: FlowSandboxService,
    @Reference(service = SessionManager::class)
    private val sessionManager: SessionManager
) : FlowEventHandler<SessionEvent> {

    private companion object {
        val log = contextLogger()
    }

    override val type = SessionEvent::class.java

    override fun preProcess(context: FlowEventContext<SessionEvent>): FlowEventContext<SessionEvent> {
        val checkpoint = context.checkpoint
        val sessionEvent = context.inputEventPayload

        log.info("Session event in handler: ${sessionEvent.payload}")

        val now = Instant.now()

        val updatedSessionState = sessionManager.processMessageReceived(
            sessionEvent.sessionId,
            if (checkpoint.doesExist) checkpoint.getSessionState(sessionEvent.sessionId) else null,
            sessionEvent,
            now
        )

        // Null is returned if duplicate [SessionInit]s are received
        val nextSessionEvent = sessionManager.getNextReceivedEvent(updatedSessionState)
        when (val sessionInit = nextSessionEvent?.payload) {
            is SessionInit -> {
                createInitiatedFlowCheckpoint(context, sessionInit,nextSessionEvent)
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
        val initiatingToInitiatedFlows = getInitiatingToInitiatedFlowsFromSandbox(initiatingIdentity.toCorda())
        val initiatedFlow = initiatingToInitiatedFlows[sessionInit.cpiId to sessionInit.flowName]
                ?: throw FlowProcessingException(
                    "No initiated flow found for initiating flow: ${sessionInit.flowName} in cpi: ${sessionInit.cpiId}"
                )
        val startContext = FlowStartContext.newBuilder()
            .setStatusKey(FlowKey(sessionId, initiatedIdentity))
            .setInitiatorType(FlowInitiatorType.P2P)
            .setRequestId(sessionId)
            .setIdentity(initiatedIdentity)
            .setCpiId(sessionInit.cpiId)
            .setInitiatedBy(initiatingIdentity)
            .setFlowClassName(initiatedFlow)
            .setCreatedTimestamp(Instant.now())
            .build()

        context.checkpoint.initFromNew(sessionInit.flowId, startContext, WaitingFor(WaitingForSessionInit(sessionId)))
    }

    private fun getInitiatingToInitiatedFlowsFromSandbox(initiatedIdentity: HoldingIdentity): Map<Pair<String, String>, String> {
        return flowSandboxService.get(initiatedIdentity).getObjectByKey(FlowSandboxContextTypes.INITIATING_TO_INITIATED_FLOWS)
            ?: throw FlowProcessingException("Sandbox for identity: $initiatedIdentity has not been initialised correctly")
    }
}