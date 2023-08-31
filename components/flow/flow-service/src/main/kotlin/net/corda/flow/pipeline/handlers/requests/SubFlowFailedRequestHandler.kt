package net.corda.flow.pipeline.handlers.requests

import net.corda.data.flow.state.session.SessionStateType
import net.corda.data.flow.state.waiting.WaitingFor
import net.corda.data.flow.state.waiting.Wakeup
import net.corda.flow.fiber.FlowIORequest
import net.corda.flow.pipeline.events.FlowEventContext
import net.corda.flow.pipeline.exceptions.FlowFatalException
import net.corda.flow.pipeline.sessions.FlowSessionManager
import net.corda.flow.pipeline.sessions.FlowSessionStateException
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import java.time.Instant

@Component(service = [FlowRequestHandler::class])
class SubFlowFailedRequestHandler @Activate constructor(
    @Reference(service = FlowSessionManager::class)
    private val flowSessionManager: FlowSessionManager,
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
            val sessionToError = request.sessionIds - flowSessionManager.getSessionsWithStatuses(
                checkpoint,
                request.sessionIds,
                setOf(SessionStateType.ERROR, SessionStateType.CLOSED)
            ).map { it.sessionId }.toSet()

            checkpoint.putSessionStates(
                flowSessionManager.sendErrorMessages(
                    checkpoint,
                    sessionToError,
                    request.throwable,
                    Instant.now()
                )
            )
        } catch (e: FlowSessionStateException) {
            // TODO CORE-4850 Wakeup with error when session does not exist
            throw FlowFatalException(e.message, e)
        }

        return context
    }
}
