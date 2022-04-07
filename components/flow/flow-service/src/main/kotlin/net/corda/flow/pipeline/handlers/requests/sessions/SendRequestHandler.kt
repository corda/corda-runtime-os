package net.corda.flow.pipeline.handlers.requests.sessions

import net.corda.data.flow.event.FlowEvent
import net.corda.data.flow.event.Wakeup
import net.corda.data.flow.state.waiting.WaitingFor
import net.corda.flow.fiber.FlowIORequest
import net.corda.flow.pipeline.FlowEventContext
import net.corda.flow.pipeline.handlers.addOrReplaceSession
import net.corda.flow.pipeline.handlers.requests.FlowRequestHandler
import net.corda.flow.pipeline.handlers.requests.requireCheckpoint
import net.corda.flow.pipeline.sessions.FlowSessionManager
import net.corda.messaging.api.records.Record
import net.corda.schema.Schemas
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import java.time.Instant

@Component(service = [FlowRequestHandler::class])
class SendRequestHandler @Activate constructor(
    @Reference(service = FlowSessionManager::class)
    private val flowSessionManager: FlowSessionManager
) : FlowRequestHandler<FlowIORequest.Send> {

    override val type = FlowIORequest.Send::class.java

    override fun getUpdatedWaitingFor(context: FlowEventContext<Any>, request: FlowIORequest.Send): WaitingFor {
        return WaitingFor(net.corda.data.flow.state.waiting.Wakeup())
    }

    override fun postProcess(context: FlowEventContext<Any>, request: FlowIORequest.Send): FlowEventContext<Any> {
        val checkpoint = requireCheckpoint(context)

        flowSessionManager.sendDataMessages(checkpoint, request.sessionToPayload, Instant.now()).forEach { updatedSessionState ->
            checkpoint.addOrReplaceSession(updatedSessionState)
        }

        val wakeup = Record(
            topic = Schemas.Flow.FLOW_EVENT_TOPIC,
            key = checkpoint.flowKey,
            value = FlowEvent(checkpoint.flowKey, Wakeup())
        )

        return context.copy(outputRecords = context.outputRecords + wakeup)
    }
}