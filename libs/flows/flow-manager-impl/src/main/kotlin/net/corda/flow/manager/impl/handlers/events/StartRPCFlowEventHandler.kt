package net.corda.flow.manager.impl.handlers.events

import net.corda.data.flow.event.StartRPCFlow
import net.corda.data.flow.state.Checkpoint
import net.corda.data.flow.state.StateMachineState
import net.corda.flow.manager.impl.FlowEventContext
import net.corda.flow.manager.impl.handlers.FlowProcessingException
import net.corda.flow.fiber.FlowContinuation
import net.corda.v5.base.util.contextLogger
import org.osgi.service.component.annotations.Component
import java.nio.ByteBuffer

@Component(service = [FlowEventHandler::class])
class StartRPCFlowEventHandler : FlowEventHandler<StartRPCFlow> {

    private companion object {
        val log = contextLogger()
    }

    override val type = StartRPCFlow::class.java

    override fun preProcess(context: FlowEventContext<StartRPCFlow>): FlowEventContext<StartRPCFlow> {
        requireNoExistingCheckpoint(context)
        val state = StateMachineState.newBuilder()
            .setClientId(context.inputEventPayload.clientId)
            .setSuspendCount(0)
            .setIsKilled(false)
            .setInitiatedBy(ByteBuffer.wrap(context.inputEventPayload.clientId.toByteArray()))
            .setWaitingFor(null)
            .setSuspendedOn(null)
            .setFlowClassName(context.inputEventPayload.flowClassName)
            .build()
        val checkpoint = Checkpoint.newBuilder()
            .setFlowKey(context.inputEvent.flowKey)
            .setFiber(ByteBuffer.wrap(byteArrayOf()))
            .setCpiId(context.inputEventPayload.cpiId)
            .setFlowState(state)
            .setSessions(emptyList())
            .build()
        return context.copy(checkpoint = checkpoint)
    }

    private fun requireNoExistingCheckpoint(context: FlowEventContext<StartRPCFlow>) {
        if (context.checkpoint != null) {
            val message = "Flow start event for ${context.inputEvent.flowKey} should have been deduplicated and will not be started"
            log.error(message)
            throw FlowProcessingException(message)
        }
    }

    override fun resumeOrContinue(context: FlowEventContext<StartRPCFlow>): FlowContinuation {
        return FlowContinuation.Run()
    }

    override fun postProcess(context: FlowEventContext<StartRPCFlow>): FlowEventContext<StartRPCFlow> {
        return context
    }
}