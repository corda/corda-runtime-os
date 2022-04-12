package net.corda.flow.pipeline.handlers.requests.sessions

import net.corda.data.flow.event.Wakeup
import net.corda.data.flow.state.session.SessionStateType
import net.corda.data.flow.state.waiting.SessionConfirmation
import net.corda.data.flow.state.waiting.SessionConfirmationType
import net.corda.data.flow.state.waiting.WaitingFor
import net.corda.flow.fiber.FlowIORequest
import net.corda.flow.pipeline.FlowEventContext
import net.corda.flow.pipeline.factory.RecordFactory
import net.corda.flow.pipeline.handlers.requests.FlowRequestHandler
import net.corda.flow.pipeline.sessions.FlowSessionManager
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import java.time.Instant

@Component(service = [FlowRequestHandler::class])
class CloseSessionsRequestHandler @Activate constructor(
    @Reference(service = FlowSessionManager::class)
    private val flowSessionManager: FlowSessionManager,
    @Reference(service = RecordFactory::class)
    private val recordFactory: RecordFactory
) : FlowRequestHandler<FlowIORequest.CloseSessions> {

    private companion object {
        val CLOSED_STATUSES = listOf(SessionStateType.CLOSED, SessionStateType.WAIT_FOR_FINAL_ACK)
    }

    override val type = FlowIORequest.CloseSessions::class.java

    override fun getUpdatedWaitingFor(context: FlowEventContext<Any>, request: FlowIORequest.CloseSessions): WaitingFor {
        return WaitingFor(SessionConfirmation(request.sessions.toList(), SessionConfirmationType.CLOSE))
    }

    override fun postProcess(context: FlowEventContext<Any>, request: FlowIORequest.CloseSessions): FlowEventContext<Any> {
        val checkpoint = context.checkpoint

        val haveSessionsAlreadyBeenClosed = flowSessionManager.areAllSessionsInStatuses(
            checkpoint,
            request.sessions.toList(),
            CLOSED_STATUSES
        )

        flowSessionManager.sendCloseMessages(checkpoint, request.sessions.toList(), Instant.now()).map { updatedSessionState ->
            checkpoint.putSessionState(updatedSessionState)
        }

        return if (haveSessionsAlreadyBeenClosed) {
            val record = recordFactory.createFlowEventRecord(checkpoint.flowId, Wakeup())
            context.copy(outputRecords = context.outputRecords + listOf(record))
        } else {
            context
        }
    }
}