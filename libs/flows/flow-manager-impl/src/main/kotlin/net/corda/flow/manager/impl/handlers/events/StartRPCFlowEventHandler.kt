package net.corda.flow.manager.impl.handlers.events

import net.corda.data.flow.event.StartRPCFlow
import net.corda.data.flow.state.Checkpoint
import net.corda.data.flow.state.StateMachineState
import net.corda.flow.manager.FlowEventContext
import net.corda.flow.statemachine.FlowContinuation
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
        return if (!context.isNewFlow) {
            context
        } else {
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
            context.copy(checkpoint = checkpoint)
        }
    }

    override fun resumeOrContinue(context: FlowEventContext<StartRPCFlow>): FlowContinuation {
        return if (!context.isNewFlow) {
            log.warn("Skipping duplicate start flow request with key: ${context.inputEvent.flowKey}")
            FlowContinuation.Continue
        } else {
            FlowContinuation.Run()
        }
    }

    override fun postProcess(context: FlowEventContext<StartRPCFlow>): FlowEventContext<StartRPCFlow> {
        return context
    }
}