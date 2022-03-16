package net.corda.flow.pipeline.handlers.requests

import net.corda.data.flow.event.FlowEvent
import net.corda.data.flow.state.waiting.WaitingFor
import net.corda.data.flow.state.waiting.Wakeup
import net.corda.flow.fiber.FlowIORequest
import net.corda.flow.pipeline.FlowEventContext
import net.corda.messaging.api.records.Record
import net.corda.schema.Schemas
import org.osgi.service.component.annotations.Component

@Component(service = [FlowRequestHandler::class])
class SubFlowFinishedRequestHandler : FlowRequestHandler<FlowIORequest.SubFlowFinished> {

    override val type = FlowIORequest.SubFlowFinished::class.java

    override fun getUpdatedWaitingFor(context: FlowEventContext<Any>, request: FlowIORequest.SubFlowFinished): WaitingFor {
        return WaitingFor(Wakeup())
    }

    override fun postProcess(
        context: FlowEventContext<Any>,
        request: FlowIORequest.SubFlowFinished
    ): FlowEventContext<Any> {
        val checkpoint = requireCheckpoint(context)

        /*
         * TODOs: Once the session management logic is implemented, we need to add logic here
         * to access the flow stack item to determine if any session clean up is required.
         */

        val record = Record(
            topic = Schemas.Flow.FLOW_EVENT_TOPIC,
            key = checkpoint.flowKey,
            value = FlowEvent(checkpoint.flowKey, net.corda.data.flow.event.Wakeup())
        )
        return context.copy(outputRecords = context.outputRecords + record)
    }
}