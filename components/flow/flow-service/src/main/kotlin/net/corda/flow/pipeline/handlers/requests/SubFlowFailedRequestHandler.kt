package net.corda.flow.pipeline.handlers.requests

import java.time.Instant
import net.corda.data.flow.state.session.SessionStateType
import net.corda.data.flow.state.waiting.WaitingFor
import net.corda.data.flow.state.waiting.Wakeup
import net.corda.flow.fiber.FlowIORequest
import net.corda.flow.pipeline.events.FlowEventContext
import net.corda.flow.pipeline.exceptions.FlowFatalException
import net.corda.flow.pipeline.factory.FlowRecordFactory
import net.corda.flow.pipeline.sessions.FlowSessionManager
import net.corda.flow.pipeline.sessions.FlowSessionStateException
import net.corda.flow.state.FlowCheckpoint
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference

@Component(service = [FlowRequestHandler::class])
class SubFlowFailedRequestHandler @Activate constructor(
    @Reference(service = FlowSessionManager::class)
    private val flowSessionManager: FlowSessionManager,
    @Reference(service = FlowRecordFactory::class)
    private val flowRecordFactory: FlowRecordFactory
) : FlowRequestHandler<FlowIORequest.SubFlowFailed> {
    override val type = FlowIORequest.SubFlowFailed::class.java

    override fun getUpdatedWaitingFor(
        context: FlowEventContext<Any>,
        request: FlowIORequest.SubFlowFailed
    ): WaitingFor {
        return WaitingFor(Wakeup())
    }

    override fun postProcess(
        context: FlowEventContext<Any>,
        request: FlowIORequest.SubFlowFailed
    ): FlowEventContext<Any> {
        val checkpoint = context.checkpoint
        try {
            checkpoint.putSessionStates(flowSessionManager.sendErrorMessages(
                checkpoint,
                getSessionsToError(checkpoint, request),
                request.throwable,
                Instant.now()
            ))
        } catch (e: FlowSessionStateException) {
            // TODO CORE-4850 Wakeup with error when session does not exist
            throw FlowFatalException(e.message, e)
        }

        val record = flowRecordFactory.createFlowEventRecord(checkpoint.flowId, net.corda.data.flow.event.Wakeup())
        return context.copy(outputRecords = context.outputRecords + record)
    }

    private fun getSessionsToError(checkpoint: FlowCheckpoint, request: FlowIORequest.SubFlowFailed): List<String> {
        val erroredSessions =
            flowSessionManager.getSessionsWithStatus(checkpoint, request.sessionIds, SessionStateType.ERROR)
        val closedSessions =
            flowSessionManager.getSessionsWithStatus(checkpoint, request.sessionIds, SessionStateType.CLOSED)

        return request.sessionIds - (erroredSessions + closedSessions).map { it.sessionId }
    }
}
