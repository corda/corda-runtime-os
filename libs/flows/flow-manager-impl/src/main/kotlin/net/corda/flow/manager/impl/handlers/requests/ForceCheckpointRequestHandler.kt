package net.corda.flow.manager.impl.handlers.requests

import net.corda.data.flow.event.FlowEvent
import net.corda.data.flow.event.Wakeup
import net.corda.data.flow.request.ForceCheckpointRequest
import net.corda.flow.manager.FlowEventContext
import net.corda.flow.manager.impl.handlers.requests.FlowRequestHandler
import net.corda.flow.manager.impl.handlers.requests.setCheckpointFlowIORequest
import net.corda.flow.statemachine.requests.FlowIORequest
import net.corda.messaging.api.records.Record
import org.osgi.service.component.annotations.Component
import net.corda.schema.Schemas

@Component(service = [FlowRequestHandler::class])
class ForceCheckpointRequestHandler : FlowRequestHandler<FlowIORequest.ForceCheckpoint> {

    override val type = FlowIORequest.ForceCheckpoint::class.java

    override fun postProcess(context: FlowEventContext<Any>, request: FlowIORequest.ForceCheckpoint): FlowEventContext<Any> {
        context.setCheckpointFlowIORequest(ForceCheckpointRequest())
        val checkpoint = context.checkpoint!!
        val record = Record(
            topic = Schemas.FLOW_EVENT_TOPIC,
            key = checkpoint.flowKey,
            value = FlowEvent(checkpoint.flowKey, Wakeup())
        )
        return context.copy(outputRecords = context.outputRecords + record)
    }
}