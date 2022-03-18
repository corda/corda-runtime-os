package net.corda.flow.pipeline.handlers.requests

import net.corda.data.flow.state.waiting.WaitingFor
import net.corda.data.flow.state.waiting.Wakeup
import net.corda.flow.fiber.FlowIORequest
import net.corda.flow.pipeline.FlowEventContext
import net.corda.flow.pipeline.factory.RecordFactory
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference

@Component(service = [FlowRequestHandler::class])
class SubFlowFinishedRequestHandler @Activate constructor(
    @Reference(service = FlowSessionManager::class)
    private val flowSessionManager: FlowSessionManager,
    @Reference(service = RecordFactory::class)
    private val recordFactory: RecordFactory
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
        val checkpoint = context.checkpoint

        return if (subFlowHasSessionsToClose(checkpoint, request)) {
            flowSessionManager.sendCloseMessages(checkpoint, request.flowStackItem.sessionIds, Instant.now())
                .map { updatedSessionState -> checkpoint.addOrReplaceSession(updatedSessionState) }
            context
        } else {
            val record = recordFactory.createFlowEventRecord(checkpoint.flowId, net.corda.data.flow.event.Wakeup())
            return context.copy(outputRecords = context.outputRecords + record)
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