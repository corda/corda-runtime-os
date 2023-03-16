package net.corda.flow.pipeline.handlers.requests

import net.corda.data.flow.event.Wakeup
import net.corda.data.flow.state.waiting.WaitingFor
import net.corda.flow.fiber.FlowIORequest
import net.corda.flow.pipeline.events.FlowEventContext
import net.corda.flow.pipeline.factory.FlowRecordFactory
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference

@Component(service = [FlowRequestHandler::class])
class ForceCheckpointRequestHandler @Activate constructor(
    @Reference(service = FlowRecordFactory::class)
    private val flowRecordFactory: FlowRecordFactory
) : FlowRequestHandler<FlowIORequest.ForceCheckpoint> {

    override val type = FlowIORequest.ForceCheckpoint::class.java

    override fun getUpdatedWaitingFor(
        context: FlowEventContext<Any>,
        request: FlowIORequest.ForceCheckpoint
    ): WaitingFor {
        return WaitingFor(net.corda.data.flow.state.waiting.Wakeup())
    }

    override fun postProcess(
        context: FlowEventContext<Any>,
        request: FlowIORequest.ForceCheckpoint
    ): FlowEventContext<Any> {
        val record = flowRecordFactory.createFlowEventRecord(context.checkpoint.flowId, Wakeup())
        return context.copy(outputRecords = context.outputRecords + record)
    }
}
