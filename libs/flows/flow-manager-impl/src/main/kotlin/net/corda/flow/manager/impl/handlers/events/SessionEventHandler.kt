package net.corda.flow.manager.impl.handlers.events

import net.corda.data.flow.FlowInitiatorType
import net.corda.data.flow.FlowStartContext
import net.corda.data.flow.FlowStatusKey
import net.corda.data.flow.event.SessionEvent
import net.corda.data.flow.event.session.SessionInit
import net.corda.data.flow.state.Checkpoint
import net.corda.data.flow.state.StateMachineState
import net.corda.data.flow.state.waiting.WaitingFor
import net.corda.flow.manager.FlowSandboxContextTypes
import net.corda.flow.manager.FlowSandboxService
import net.corda.flow.manager.impl.FlowEventContext
import net.corda.flow.manager.impl.handlers.FlowProcessingException
import net.corda.flow.manager.impl.handlers.addOrReplaceSession
import net.corda.flow.manager.impl.handlers.getSession
import net.corda.flow.manager.impl.handlers.status.sessions.WaitingForSessionInit
import net.corda.sandboxgroupcontext.getObjectByKey
import net.corda.session.manager.SessionManager
import net.corda.v5.base.util.contextLogger
import net.corda.virtualnode.HoldingIdentity
import net.corda.virtualnode.toCorda
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import java.nio.ByteBuffer
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
        val sessionEvent = context.inputEventPayload

        log.info("Session event in handler: ${sessionEvent.payload}")

        val now = Instant.now()

        val updatedSessionState = sessionManager.processMessageReceived(
            sessionEvent.sessionId,
            context.checkpoint?.getSession(sessionEvent.sessionId),
            sessionEvent, now
        )

        when (val sessionInit = sessionEvent.payload) {
            is SessionInit -> {
                val checkpoint = createInitiatedFlowCheckpoint(context, sessionEvent.sessionId, sessionInit)
                checkpoint.sessions.add(updatedSessionState)
                return context.copy(checkpoint = checkpoint)
            }
        }

        context.checkpoint?.addOrReplaceSession(updatedSessionState)

        return context
    }

    private fun createInitiatedFlowCheckpoint(context: FlowEventContext<*>, sessionId: String, sessionInit: SessionInit): Checkpoint {
        val initiatingToInitiatedFlows = getInitiatingToInitiatedFlowsFromSandbox(sessionInit.initiatedIdentity.toCorda())
        val initiatedFlow = initiatingToInitiatedFlows[sessionInit.cpiId to sessionInit.flowName] ?: throw FlowProcessingException(
            "No initiated flow found for initiating flow: ${sessionInit.flowName} in cpi: ${sessionInit.cpiId}"
        )

        return if (context.checkpoint != null) {
            context.checkpoint
        } else {
            val state = StateMachineState.newBuilder()
                .setSuspendCount(0)
                .setIsKilled(false)
                .setWaitingFor(WaitingFor(WaitingForSessionInit(sessionId)))
                .setSuspendedOn(null)
                .build()
            val startContext = FlowStartContext.newBuilder()
                .setStatusKey(FlowStatusKey(context.inputEvent.flowKey.flowId, context.inputEvent.flowKey.identity))
                .setInitiatorType(FlowInitiatorType.P2P)
                .setRequestId(sessionId)
                .setIdentity(context.inputEvent.flowKey.identity)
                .setCpiId(sessionInit.cpiId)
                .setInitiatedBy(sessionInit.initiatingIdentity)
                .setFlowClassName(initiatedFlow)
                .setCreatedTimestamp(Instant.now())
                .build()
            Checkpoint.newBuilder()
                .setFlowKey(sessionInit.flowKey)
                .setFiber(ByteBuffer.wrap(byteArrayOf()))
                .setFlowStartContext(startContext)
                .setFlowState(state)
                .setSessions(mutableListOf())
                .setFlowStackItems(mutableListOf())
                .build()
        }
    }

    private fun getInitiatingToInitiatedFlowsFromSandbox(holdingIdentity: HoldingIdentity): Map<Pair<String, String>, String> {
        return flowSandboxService.get(holdingIdentity).getObjectByKey(FlowSandboxContextTypes.INITIATING_TO_INITIATED_FLOWS)
            ?: throw FlowProcessingException("Sandbox for identity: $holdingIdentity has not been initialised correctly")
    }
}