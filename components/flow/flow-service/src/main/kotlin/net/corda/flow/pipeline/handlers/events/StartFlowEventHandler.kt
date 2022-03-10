package net.corda.flow.pipeline.handlers.events

import net.corda.data.flow.event.StartFlow
import net.corda.data.flow.state.Checkpoint
import net.corda.data.flow.state.StateMachineState
import net.corda.data.flow.state.waiting.WaitingFor
import net.corda.flow.pipeline.FlowEventContext
import net.corda.flow.pipeline.FlowProcessingException
import net.corda.flow.pipeline.handlers.waiting.WaitingForStartFlow
import net.corda.v5.base.util.contextLogger
import org.osgi.service.component.annotations.Component
import java.nio.ByteBuffer

@Component(service = [FlowEventHandler::class])
class StartFlowEventHandler : FlowEventHandler<StartFlow> {

    private companion object {
        val log = contextLogger()
    }

    override val type = StartFlow::class.java

    override fun preProcess(context: FlowEventContext<StartFlow>): FlowEventContext<StartFlow> {
        requireNoExistingCheckpoint(context)
        val state = StateMachineState.newBuilder()
            .setSuspendCount(0)
            .setIsKilled(false)
            .setWaitingFor(WaitingFor(WaitingForStartFlow))
            .setSuspendedOn(null)
            .build()
        val checkpoint = Checkpoint.newBuilder()
            .setFlowKey(context.inputEvent.flowKey)
            .setFiber(ByteBuffer.wrap(byteArrayOf()))
            .setFlowStartContext(context.inputEventPayload.startContext)
            .setFlowState(state)
            .setSessions(mutableListOf())
            .setFlowStackItems(mutableListOf())
            .build()
        return context.copy(checkpoint = checkpoint)
    }

    private fun requireNoExistingCheckpoint(context: FlowEventContext<StartFlow>) {
        if (context.checkpoint != null) {
            val message = "Flow start event for ${context.inputEvent.flowKey} should have been deduplicated and will not be started"
            log.error(message)
            throw FlowProcessingException(message)
        }
    }
}