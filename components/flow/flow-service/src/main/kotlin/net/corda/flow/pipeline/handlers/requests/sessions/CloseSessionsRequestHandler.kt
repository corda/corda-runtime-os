package net.corda.flow.pipeline.handlers.requests.sessions

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
import net.corda.flow.pipeline.handlers.requests.FlowRequestHandler
import net.corda.flow.pipeline.sessions.FlowSessionManager
import net.corda.flow.pipeline.sessions.FlowSessionStateException
import net.corda.flow.state.FlowCheckpoint
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference

@Component(service = [FlowRequestHandler::class])
class CloseSessionsRequestHandler @Activate constructor(
    @Reference(service = FlowSessionManager::class)
    private val flowSessionManager: FlowSessionManager,
    @Reference(service = FlowRecordFactory::class)
    private val flowRecordFactory: FlowRecordFactory
) : FlowRequestHandler<FlowIORequest.CloseSessions> {

    override val type = FlowIORequest.CloseSessions::class.java

    override fun getUpdatedWaitingFor(context: FlowEventContext<Any>, request: FlowIORequest.CloseSessions): WaitingFor {
        val sessionsToClose = try {
            getSessionsToClose(context.checkpoint, request)
        } catch (e: IllegalArgumentException) {
            // TODO Wakeup flow with an error
            throw FlowFatalException(
                e.message ?: "An error occurred in the platform - A session in ${request.sessions} was missing from the checkpoint",
                e
            )
        }
        return if (sessionsToClose.isEmpty()) {
            WaitingFor(net.corda.data.flow.state.waiting.Wakeup())
        } else {
            WaitingFor(SessionConfirmation(sessionsToClose, SessionConfirmationType.CLOSE))
        }
    }

    override fun postProcess(context: FlowEventContext<Any>, request: FlowIORequest.CloseSessions): FlowEventContext<Any> {
        val checkpoint = context.checkpoint

        val hasNoSessionsOrAllClosed = try {
            val sessionsToClose = getSessionsToClose(checkpoint, request)

            checkpoint.putSessionStates(flowSessionManager.sendCloseMessages(checkpoint, sessionsToClose, Instant.now()))

            sessionsToClose.isEmpty() || flowSessionManager.doAllSessionsHaveStatus(checkpoint, sessionsToClose, SessionStateType.CLOSED)
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

    private fun getSessionsToClose(checkpoint: FlowCheckpoint, request: FlowIORequest.CloseSessions): List<String> {
        val sessions = request.sessions.toList()
        val erroredSessions = flowSessionManager.getSessionsWithStatus(checkpoint, sessions, SessionStateType.ERROR)
        return sessions - erroredSessions.map { it.sessionId }
    }
}
