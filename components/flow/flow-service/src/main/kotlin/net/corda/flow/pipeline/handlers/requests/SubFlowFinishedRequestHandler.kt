package net.corda.flow.pipeline.handlers.requests

import java.time.Instant
import net.corda.data.flow.event.Wakeup
import net.corda.data.flow.state.session.SessionStateType
import net.corda.data.flow.state.waiting.SessionConfirmation
import net.corda.data.flow.state.waiting.SessionConfirmationType
import net.corda.data.flow.state.waiting.WaitingFor
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
class SubFlowFinishedRequestHandler @Activate constructor(
    @Reference(service = FlowSessionManager::class)
    private val flowSessionManager: FlowSessionManager,
    @Reference(service = FlowRecordFactory::class)
    private val flowRecordFactory: FlowRecordFactory
) : FlowRequestHandler<FlowIORequest.SubFlowFinished> {

    override val type = FlowIORequest.SubFlowFinished::class.java

    override fun getUpdatedWaitingFor(
        context: FlowEventContext<Any>,
        request: FlowIORequest.SubFlowFinished
    ): WaitingFor {
        val sessionsToClose = try {
            getSessionsToClose(context.checkpoint, request)
        } catch (e: FlowSessionStateException) {
            // TODO CORE-4850 Wakeup with error when session does not exist
            throw FlowFatalException(e.message, e)
        }
        return if (sessionsToClose.isEmpty()) {
            WaitingFor(net.corda.data.flow.state.waiting.Wakeup())
        } else {
            WaitingFor(SessionConfirmation(sessionsToClose, SessionConfirmationType.CLOSE))
        }
    }

    override fun postProcess(
        context: FlowEventContext<Any>,
        request: FlowIORequest.SubFlowFinished
    ): FlowEventContext<Any> {
        val checkpoint = context.checkpoint
        val hasNoSessionsOrAllClosed = try {
            val sessionsToClose = getSessionsToClose(checkpoint, request)

            checkpoint.putSessionStates(flowSessionManager.sendCloseMessages(checkpoint, sessionsToClose, Instant.now()))

            sessionsToClose.isEmpty() || flowSessionManager.doAllSessionsHaveStatus(
                checkpoint,
                sessionsToClose,
                SessionStateType.CLOSED
            )
        } catch (e: FlowSessionStateException) {
            // TODO CORE-4850 Wakeup with error when session does not exist
            throw FlowFatalException(e.message, e)
        }

        return if (hasNoSessionsOrAllClosed) {
            val record = flowRecordFactory.createFlowEventRecord(checkpoint.flowId, Wakeup())
            context.copy(outputRecords = context.outputRecords + listOf(record))
        } else {
            context
        }
    }

    private fun getSessionsToClose(checkpoint: FlowCheckpoint, request: FlowIORequest.SubFlowFinished): List<String> {
        val erroredSessions =
            flowSessionManager.getSessionsWithStatus(checkpoint, request.sessionIds, SessionStateType.ERROR)

        return request.sessionIds - erroredSessions.map { it.sessionId }
    }
}
