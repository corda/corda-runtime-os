package net.corda.flow.pipeline.handlers.requests

import net.corda.data.flow.state.waiting.WaitingFor
import net.corda.data.flow.state.waiting.Wakeup
import net.corda.flow.fiber.FlowIORequest
import net.corda.flow.pipeline.events.FlowEventContext
import net.corda.flow.pipeline.factory.FlowMessageFactory
import net.corda.flow.pipeline.factory.FlowRecordFactory
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference

@Component(service = [FlowRequestHandler::class])
class InitialCheckpointRequestHandler @Activate constructor(
    @Reference(service = FlowMessageFactory::class)
    private val flowMessageFactory: FlowMessageFactory,
    @Reference(service = FlowRecordFactory::class)
    private val flowRecordFactory: FlowRecordFactory
) : FlowRequestHandler<FlowIORequest.InitialCheckpoint> {

    override val type = FlowIORequest.InitialCheckpoint::class.java

    override fun getUpdatedWaitingFor(
        context: FlowEventContext<Any>,
        request: FlowIORequest.InitialCheckpoint
    ): WaitingFor {
        return WaitingFor(Wakeup())
    }

    override fun postProcess(
        context: FlowEventContext<Any>,
        request: FlowIORequest.InitialCheckpoint
    ): FlowEventContext<Any> {
        val checkpoint = context.checkpoint

        val status = flowMessageFactory.createFlowStartedStatusMessage(checkpoint)

        val records = listOf(
            flowRecordFactory.createFlowEventRecord(checkpoint.flowId,net.corda.data.flow.event.Wakeup()),
            flowRecordFactory.createFlowStatusRecord(status)
        )

        return context.copy(outputRecords = context.outputRecords + records)
    }
}
