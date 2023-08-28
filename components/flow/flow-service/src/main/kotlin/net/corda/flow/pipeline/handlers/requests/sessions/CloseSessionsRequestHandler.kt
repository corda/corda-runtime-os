package net.corda.flow.pipeline.handlers.requests.sessions

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
import net.corda.flow.pipeline.handlers.requests.sessions.service.CloseSessionService
import net.corda.flow.pipeline.sessions.FlowSessionManager
import net.corda.flow.pipeline.sessions.FlowSessionStateException
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference


@Component(service = [FlowRequestHandler::class])
class CloseSessionsRequestHandler @Activate constructor(
    @Reference(service = FlowSessionManager::class)
    private val flowSessionManager: FlowSessionManager,
    @Reference(service = FlowRecordFactory::class)
    private val flowRecordFactory: FlowRecordFactory,
    @Reference(service = CloseSessionService::class)
    private val closeSessionService: CloseSessionService
) : FlowRequestHandler<FlowIORequest.CloseSessions> {

    override val type = FlowIORequest.CloseSessions::class.java

    private fun getSessionsToClose(request: FlowIORequest.CloseSessions): List<String> {
        return request.sessions.toMutableList()
    }

    override fun getUpdatedWaitingFor(
        context: FlowEventContext<Any>,
        request: FlowIORequest.CloseSessions
    ): WaitingFor {
        val sessionsToClose = try {
            closeSessionService.getSessionsToCloseForWaitingFor(context.checkpoint, getSessionsToClose(request))
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

    override fun postProcess(
        context: FlowEventContext<Any>,
        request: FlowIORequest.CloseSessions
    ): FlowEventContext<Any> {
        val checkpoint = context.checkpoint

        val hasNoSessionsOrAllClosed = try {

            closeSessionService.handleCloseForSessions(getSessionsToClose(request), checkpoint)
            getSessionsToClose(request).isEmpty() || flowSessionManager.doAllSessionsHaveStatus(
                checkpoint,
                getSessionsToClose(request),
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
}
