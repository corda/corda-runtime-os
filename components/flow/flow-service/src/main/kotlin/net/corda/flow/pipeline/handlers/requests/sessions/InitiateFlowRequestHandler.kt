package net.corda.flow.pipeline.handlers.requests.sessions

import java.time.Instant
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
import net.corda.flow.utils.keyValuePairListOf
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference

@Component(service = [FlowRequestHandler::class])
class InitiateFlowRequestHandler @Activate constructor(
    @Reference(service = FlowSessionManager::class)
    private val flowSessionManager: FlowSessionManager,
    @Reference(service = FlowSandboxService::class)
    private val flowSandboxService: FlowSandboxService
) : FlowRequestHandler<FlowIORequest.InitiateFlow> {

    override val type = FlowIORequest.InitiateFlow::class.java

    override fun getUpdatedWaitingFor(context: FlowEventContext<Any>, request: FlowIORequest.InitiateFlow): WaitingFor {
        return WaitingFor(SessionConfirmation(request.sessionToCounterparty.keys.toList(), SessionConfirmationType.INITIATE))
    }

    override fun postProcess(
        context: FlowEventContext<Any>,
        request: FlowIORequest.InitiateFlow
    ): FlowEventContext<Any> {
        val checkpoint = context.checkpoint

        // throw an error if the session already exists (shouldn't really get here for real, but for this class, it's not valid)

        val protocolStore = try {
            flowSandboxService.get(context.checkpoint.holdingIdentity).protocolStore
        } catch (e: Exception) {
            throw FlowTransientException(
                "Failed to create the flow sandbox for identity ${context.checkpoint.holdingIdentity}: ${e.message}",
                e
            )
        }

        val initiator = checkpoint.flowStack.nearestFirst { it.isInitiatingFlow }?.flowName
            ?: throw FlowFatalException("Flow stack is empty or did not contain an initiating flow in the stack")

        val (protocolName, protocolVersions) = protocolStore.protocolsForInitiator(initiator, context)

        checkpoint.putSessionStates(
            request.sessionToCounterparty.entries.map {
                flowSessionManager.sendInitMessage(
                    checkpoint,
                    it.key,
                    it.value,
                    protocolName,
                    protocolVersions,
                    contextUserProperties = keyValuePairListOf(request.contextUserProperties),
                    contextPlatformProperties = keyValuePairListOf(request.contextPlatformProperties),
                    Instant.now()
                )
            }
        )

        return context
    }
}