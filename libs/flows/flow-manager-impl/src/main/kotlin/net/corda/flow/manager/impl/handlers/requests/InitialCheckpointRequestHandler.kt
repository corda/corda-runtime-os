package net.corda.flow.manager.impl.handlers.requests

import net.corda.data.flow.event.FlowEvent
import net.corda.data.flow.state.waiting.Wakeup
import net.corda.flow.manager.factory.FlowMessageFactory
import net.corda.flow.manager.fiber.FlowIORequest
import net.corda.flow.manager.impl.FlowEventContext
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

    override fun postProcess(
        context: FlowEventContext<Any>,
        request: FlowIORequest.InitialCheckpoint
    ): FlowEventContext<Any> {
        val checkpoint = requireCheckpoint(context)
        checkpoint.setWaitingFor(Wakeup())

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