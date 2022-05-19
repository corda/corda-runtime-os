package net.corda.flow.pipeline.handlers.requests

import net.corda.data.flow.event.Wakeup
import net.corda.data.flow.state.session.SessionStateType
import net.corda.data.flow.state.waiting.SessionConfirmation
import net.corda.data.flow.state.waiting.SessionConfirmationType
import net.corda.data.flow.state.waiting.WaitingFor
import net.corda.flow.fiber.FlowIORequest
import net.corda.flow.pipeline.FlowEventContext
import net.corda.flow.pipeline.exceptions.FlowFatalException
import net.corda.flow.pipeline.factory.FlowRecordFactory
import net.corda.flow.pipeline.sessions.FlowSessionManager
import net.corda.flow.pipeline.sessions.FlowSessionMissingException
import net.corda.flow.state.FlowCheckpoint
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import java.time.Instant

@Component(service = [FlowRequestHandler::class])
class SubFlowFinishedRequestHandler @Activate constructor(
    @Reference(service = FlowSessionManager::class)
    private val flowSessionManager: FlowSessionManager,
    @Reference(service = FlowRecordFactory::class)
    private val flowRecordFactory: FlowRecordFactory
) : FlowRequestHandler<FlowIORequest.SubFlowFinished> {

    override val type = FlowIORequest.SubFlowFinished::class.java

    override fun getUpdatedWaitingFor(context: FlowEventContext<Any>, request: FlowIORequest.SubFlowFinished): WaitingFor {
        val sessionsToClose = try {
            getSessionsToClose(context.checkpoint, request)
        } catch (e: FlowSessionMissingException) {
            // TODO CORE-4850 Wakeup with error when session does not exist
            throw FlowFatalException(e.message, context, e)
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

            flowSessionManager.sendCloseMessages(checkpoint, sessionsToClose, Instant.now())
                .forEach { updatedSessionState -> checkpoint.putSessionState(updatedSessionState) }

            sessionsToClose.isEmpty() || flowSessionManager.doAllSessionsHaveStatus(checkpoint, sessionsToClose, SessionStateType.CLOSED)
        } catch (e: FlowSessionMissingException) {
            // TODO CORE-4850 Wakeup with error when session does not exist
            throw FlowFatalException(e.message, context, e)
        }

        return if (hasNoSessionsOrAllClosed) {
            val record = flowRecordFactory.createFlowEventRecord(checkpoint.flowId, Wakeup())
            context.copy(outputRecords = context.outputRecords + listOf(record))
        } else {
            context
        }
    }

    private fun getSessionsToClose(checkpoint: FlowCheckpoint, request: FlowIORequest.SubFlowFinished): List<String> {
        val flowStackItem = request.flowStackItem
        val erroredSessions = flowSessionManager.getSessionsWithStatus(checkpoint, flowStackItem.sessionIds, SessionStateType.ERROR)
        return flowStackItem.sessionIds - erroredSessions.map { it.sessionId }
    }
}