package net.corda.flow.manager.impl.handlers.requests

import net.corda.data.flow.event.FlowEvent
import net.corda.data.flow.event.Wakeup
import net.corda.flow.fiber.requests.FlowIORequest
import net.corda.flow.manager.impl.FlowEventContext
import net.corda.messaging.api.records.Record
import net.corda.schema.Schemas.Flow.Companion.FLOW_EVENT_TOPIC
import org.osgi.service.component.annotations.Component
import net.corda.data.flow.state.waiting.Wakeup as WaitingForWakeup

@Component(service = [FlowRequestHandler::class])
class ForceCheckpointRequestHandler : FlowRequestHandler<FlowIORequest.ForceCheckpoint> {

    override val type = FlowIORequest.ForceCheckpoint::class.java

    override fun postProcess(context: FlowEventContext<Any>, request: FlowIORequest.ForceCheckpoint): FlowEventContext<Any> {
        val checkpoint = requireCheckpoint(context)
        checkpoint.setWaitingFor(WaitingForWakeup())
        val record = Record(
            topic = FLOW_EVENT_TOPIC,
            key = checkpoint.flowKey,
            value = FlowEvent(checkpoint.flowKey, Wakeup())
        )
        return context.copy(outputRecords = context.outputRecords + record)
    }
}