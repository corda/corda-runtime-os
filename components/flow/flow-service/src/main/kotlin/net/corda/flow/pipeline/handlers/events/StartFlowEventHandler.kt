package net.corda.flow.pipeline.handlers.events

import net.corda.data.flow.event.StartFlow
import net.corda.data.flow.state.waiting.WaitingFor
import net.corda.flow.pipeline.FlowEventContext
import net.corda.flow.pipeline.handlers.waiting.WaitingForStartFlow
import org.osgi.service.component.annotations.Component

@Component(service = [FlowEventHandler::class])
class StartFlowEventHandler : FlowEventHandler<StartFlow> {

    override val type = StartFlow::class.java

    override fun preProcess(context: FlowEventContext<StartFlow>): FlowEventContext<StartFlow> {
        context.checkpoint.initFromNew(
            context.inputEvent.flowId,
            context.inputEventPayload.startContext,
            WaitingFor(WaitingForStartFlow))
        return context
    }
}