package net.corda.flow.pipeline.handlers.requests.sessions

import net.corda.data.flow.state.waiting.SessionConfirmation
import net.corda.data.flow.state.waiting.SessionConfirmationType
import net.corda.data.flow.state.waiting.WaitingFor
import net.corda.flow.fiber.FlowIORequest
import net.corda.flow.pipeline.events.FlowEventContext
import net.corda.flow.pipeline.exceptions.FlowFatalException
import net.corda.flow.pipeline.handlers.requests.FlowRequestHandler
import net.corda.flow.pipeline.handlers.requests.sessions.service.CloseSessionService
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference

@Component(service = [FlowRequestHandler::class])
class CloseSessionsRequestHandler @Activate constructor(
    @Reference(service = CloseSessionService::class)
    private val closeSessionService: CloseSessionService
) : FlowRequestHandler<FlowIORequest.CloseSessions> {

    override val type = FlowIORequest.CloseSessions::class.java

    private fun getSessionsToClose(request: FlowIORequest.CloseSessions): List<String> {
        return request.sessions.toList()
    }

    override fun getUpdatedWaitingFor(
        context: FlowEventContext<Any>,
        request: FlowIORequest.CloseSessions
    ): WaitingFor {
        val sessionsToClose = try {
            closeSessionService.getSessionsToCloseForWaitingFor(context.checkpoint, getSessionsToClose(request))
        } catch (e: Exception) {
            val msg = e.message ?: "An error occurred in the platform - A session in ${request.sessions} was missing from the checkpoint"
            throw FlowFatalException(msg, e)
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
        try {
            closeSessionService.handleCloseForSessions(checkpoint, getSessionsToClose(request))
        } catch (e: Exception) {
            // TODO CORE-4850 Wakeup with error when session does not exist
            val msg = e.message ?: "An error occurred in the platform - A session in ${request.sessions} was missing from the checkpoint"
            throw FlowFatalException(msg, e)
        }
        return context
    }
}
