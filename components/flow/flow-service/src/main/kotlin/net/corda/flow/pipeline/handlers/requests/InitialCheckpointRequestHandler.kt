package net.corda.flow.pipeline.handlers.requests

import net.corda.data.flow.event.FlowEvent
import net.corda.data.flow.state.waiting.WaitingFor
import net.corda.data.flow.state.waiting.Wakeup
import net.corda.flow.fiber.FlowIORequest
import net.corda.flow.pipeline.FlowEventContext
import net.corda.flow.pipeline.factory.FlowMessageFactory
import net.corda.messaging.api.records.Record
import net.corda.schema.Schemas
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference

@Component(service = [FlowRequestHandler::class])
class InitialCheckpointRequestHandler @Activate constructor(
    @Reference(service = FlowMessageFactory::class)
    private val flowMessageFactory: FlowMessageFactory
) : FlowRequestHandler<FlowIORequest.InitialCheckpoint> {

    override val type = FlowIORequest.InitialCheckpoint::class.java

    override fun getUpdatedWaitingFor(context: FlowEventContext<Any>, request: FlowIORequest.InitialCheckpoint): WaitingFor {
        return WaitingFor(Wakeup())
    }

    override fun postProcess(
        context: FlowEventContext<Any>,
        request: FlowIORequest.InitialCheckpoint
    ): FlowEventContext<Any> {
        val checkpoint = requireCheckpoint(context)

        val status = flowMessageFactory.createFlowStartedStatusMessage(checkpoint)

        val records = listOf(
            Record(
                topic = Schemas.Flow.FLOW_EVENT_TOPIC,
                key = checkpoint.flowKey,
                value = FlowEvent(checkpoint.flowKey, net.corda.data.flow.event.Wakeup())
            ),
            Record(
                topic = Schemas.Flow.FLOW_STATUS_TOPIC,
                key = status.key,
                value = status
            )
        )

        return context.copy(outputRecords = context.outputRecords + records)
    }
}