package net.corda.flow.pipeline.handlers.events

import net.corda.data.flow.event.StartFlow
import net.corda.data.flow.state.waiting.WaitingFor
import net.corda.flow.pipeline.CheckpointInitializer
import net.corda.flow.pipeline.FlowEventContext
import net.corda.flow.pipeline.handlers.waiting.WaitingForStartFlow
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference

@Component(service = [FlowEventHandler::class])
class StartFlowEventHandler @Activate constructor(
    @Reference(service = CheckpointInitializer::class)
    private val checkpointInitializer: CheckpointInitializer
) : FlowEventHandler<StartFlow> {

    override val type = StartFlow::class.java

    override fun preProcess(context: FlowEventContext<StartFlow>): FlowEventContext<StartFlow> {
        checkpointInitializer.initialize(context.checkpoint, context.inputEventPayload.startContext, WaitingFor(WaitingForStartFlow))
        return context
    }
}