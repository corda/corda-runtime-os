package net.corda.flow.pipeline.handlers.requests.sessions

import net.corda.data.flow.event.FlowEvent
import net.corda.data.flow.event.Wakeup
import net.corda.data.flow.state.waiting.SessionData
import net.corda.data.flow.state.waiting.WaitingFor
import net.corda.flow.fiber.FlowIORequest
import net.corda.flow.pipeline.FlowEventContext
import net.corda.flow.pipeline.handlers.requests.FlowRequestHandler
import net.corda.flow.pipeline.handlers.requests.requireCheckpoint
import net.corda.flow.pipeline.sessions.FlowSessionManager
import net.corda.messaging.api.records.Record
import net.corda.schema.Schemas
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference

@Component(service = [FlowRequestHandler::class])
class ReceiveRequestHandler @Activate constructor(
    @Reference(service = FlowSessionManager::class)
    private val flowSessionManager: FlowSessionManager
) : FlowRequestHandler<FlowIORequest.Receive> {

    override val type = FlowIORequest.Receive::class.java

    override fun getUpdatedWaitingFor(context: FlowEventContext<Any>, request: FlowIORequest.Receive): WaitingFor {
        return WaitingFor(SessionData(request.sessions.toList()))
    }

    override fun postProcess(context: FlowEventContext<Any>, request: FlowIORequest.Receive): FlowEventContext<Any> {
        val checkpoint = requireCheckpoint(context)
        return if (flowSessionManager.hasReceivedEvents(checkpoint, request.sessions.toList())) {
            val record = Record(
                topic = Schemas.Flow.FLOW_EVENT_TOPIC,
                key = checkpoint.flowKey,
                value = FlowEvent(checkpoint.flowKey, Wakeup())
            )
            context.copy(outputRecords = context.outputRecords + listOf(record))
        } else {
            context
        }
    }
}