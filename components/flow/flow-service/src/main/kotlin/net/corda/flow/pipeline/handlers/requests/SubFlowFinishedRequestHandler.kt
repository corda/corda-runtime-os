package net.corda.flow.pipeline.handlers.requests

import net.corda.data.flow.event.FlowEvent
import net.corda.data.flow.state.Checkpoint
import net.corda.data.flow.state.session.SessionStateType
import net.corda.data.flow.state.waiting.SessionConfirmation
import net.corda.data.flow.state.waiting.SessionConfirmationType
import net.corda.data.flow.state.waiting.WaitingFor
import net.corda.data.flow.state.waiting.Wakeup
import net.corda.flow.fiber.FlowIORequest
import net.corda.flow.pipeline.FlowEventContext
import net.corda.flow.pipeline.handlers.addOrReplaceSession
import net.corda.flow.pipeline.sessions.FlowSessionManager
import net.corda.messaging.api.records.Record
import net.corda.schema.Schemas
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import java.time.Instant

@Component(service = [FlowRequestHandler::class])
class SubFlowFinishedRequestHandler @Activate constructor(
    @Reference(service = FlowSessionManager::class)
    private val flowSessionManager: FlowSessionManager
) : FlowRequestHandler<FlowIORequest.SubFlowFinished> {

    private companion object {
        val CLOSED_STATUSES = listOf(SessionStateType.CLOSED, SessionStateType.WAIT_FOR_FINAL_ACK)
    }

    override val type = FlowIORequest.SubFlowFinished::class.java

    override fun getUpdatedWaitingFor(context: FlowEventContext<Any>, request: FlowIORequest.SubFlowFinished): WaitingFor {
        return if (subFlowHasSessionsToClose(requireCheckpoint(context), request)) {
            return WaitingFor(SessionConfirmation(request.flowStackItem.sessionIds, SessionConfirmationType.CLOSE))
        } else {
            WaitingFor(Wakeup())
        }
    }

    override fun postProcess(
        context: FlowEventContext<Any>,
        request: FlowIORequest.SubFlowFinished
    ): FlowEventContext<Any> {
        val checkpoint = requireCheckpoint(context)

        return if (subFlowHasSessionsToClose(checkpoint, request)) {
            flowSessionManager.sendCloseMessages(checkpoint, request.flowStackItem.sessionIds, Instant.now())
                .map { updatedSessionState -> checkpoint.addOrReplaceSession(updatedSessionState) }
            context
        } else {
            val record = Record(
                topic = Schemas.Flow.FLOW_EVENT_TOPIC,
                key = checkpoint.flowKey,
                value = FlowEvent(checkpoint.flowKey, net.corda.data.flow.event.Wakeup())
            )
            context.copy(outputRecords = context.outputRecords + record)
        }
    }

    private fun subFlowHasSessionsToClose(checkpoint: Checkpoint, request: FlowIORequest.SubFlowFinished): Boolean {
        return request.flowStackItem.isInitiatingFlow
                && request.flowStackItem.sessionIds.isNotEmpty()
                && !allSessionsAreClosed(checkpoint, request)
    }

    private fun allSessionsAreClosed(checkpoint: Checkpoint, request: FlowIORequest.SubFlowFinished): Boolean {
        return flowSessionManager.areAllSessionsInStatuses(
            checkpoint,
            request.flowStackItem.sessionIds,
            CLOSED_STATUSES
        )
    }
}