package net.corda.flow.pipeline.handlers.requests.sessions

import net.corda.data.flow.state.waiting.SessionConfirmation
import net.corda.data.flow.state.waiting.SessionConfirmationType
import net.corda.data.flow.state.waiting.WaitingFor
import net.corda.flow.fiber.FlowIORequest
import net.corda.flow.pipeline.FlowEventContext
import net.corda.flow.pipeline.exceptions.FlowFatalException
import net.corda.flow.pipeline.exceptions.FlowTransientException
import net.corda.flow.pipeline.handlers.requests.FlowRequestHandler
import net.corda.flow.pipeline.sandbox.FlowSandboxService
import net.corda.flow.pipeline.sessions.FlowSessionManager
import net.corda.virtualnode.toCorda
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import java.time.Instant

@Component(service = [FlowRequestHandler::class])
class InitiateFlowRequestHandler @Activate constructor(
    @Reference(service = FlowSessionManager::class)
    private val flowSessionManager: FlowSessionManager,
    @Reference(service = FlowSandboxService::class)
    private val flowSandboxService: FlowSandboxService
) : FlowRequestHandler<FlowIORequest.InitiateFlow> {

    override val type = FlowIORequest.InitiateFlow::class.java

    override fun getUpdatedWaitingFor(context: FlowEventContext<Any>, request: FlowIORequest.InitiateFlow): WaitingFor {
        return WaitingFor(SessionConfirmation(listOf(request.sessionId), SessionConfirmationType.INITIATE))
    }

    override fun postProcess(context: FlowEventContext<Any>, request: FlowIORequest.InitiateFlow): FlowEventContext<Any> {
        val checkpoint = context.checkpoint

        // throw an error if the session already exists (shouldn't really get here for real, but for this class, it's not valid)

        val protocolStore = try {
            flowSandboxService.get(context.checkpoint.holdingIdentity.toCorda()).protocolStore
        } catch (e: Exception) {
            throw FlowTransientException(
                "Failed to create the flow sandbox for identity ${context.checkpoint.holdingIdentity.toCorda()}: ${e.message}",
                context,
                e
            )
        }
        val initiator =
            checkpoint.flowStack.peek()?.flowName ?: throw FlowFatalException("Flow stack is empty", context)
        val (protocolName, protocolVersions) = protocolStore.protocolsForInitiator(initiator, context)
        checkpoint.putSessionState(
            flowSessionManager.sendInitMessage(
                checkpoint,
                request.sessionId,
                request.x500Name,
                protocolName,
                protocolVersions,
                Instant.now()
            )
        )

        return context
    }
}