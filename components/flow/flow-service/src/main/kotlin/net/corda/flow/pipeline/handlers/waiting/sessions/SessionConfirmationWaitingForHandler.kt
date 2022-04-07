package net.corda.flow.pipeline.handlers.waiting.sessions

import net.corda.data.flow.event.SessionEvent
import net.corda.data.flow.event.session.SessionAck
import net.corda.data.flow.event.session.SessionClose
import net.corda.data.flow.state.session.SessionStateType
import net.corda.data.flow.state.waiting.SessionConfirmation
import net.corda.data.flow.state.waiting.SessionConfirmationType
import net.corda.flow.fiber.FlowContinuation
import net.corda.flow.pipeline.FlowEventContext
import net.corda.flow.pipeline.FlowProcessingException
import net.corda.flow.pipeline.handlers.waiting.FlowWaitingForHandler
import net.corda.flow.pipeline.handlers.waiting.requireCheckpoint
import net.corda.flow.pipeline.sessions.FlowSessionManager
import net.corda.v5.base.exceptions.CordaRuntimeException
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference

@Component(service = [FlowWaitingForHandler::class])
class SessionConfirmationWaitingForHandler @Activate constructor(
    @Reference(service = FlowSessionManager::class)
    private val flowSessionManager: FlowSessionManager
) : FlowWaitingForHandler<SessionConfirmation> {

    private companion object {
        val CLOSED_STATUSES = listOf(SessionStateType.CLOSED)
    }

    override val type = SessionConfirmation::class.java

    override fun runOrContinue(context: FlowEventContext<*>, waitingFor: SessionConfirmation): FlowContinuation {
        val checkpoint = requireCheckpoint(context)
        return when (waitingFor.type) {
            SessionConfirmationType.INITIATE -> {
                if (context.inputEventPayload !is SessionEvent || context.inputEventPayload.payload !is SessionAck) {
                    FlowContinuation.Continue
                } else if (waitingFor.sessionIds == listOf(context.inputEventPayload.sessionId)) {
                    FlowContinuation.Run(Unit)
                } else {
                    FlowContinuation.Continue
                }
            }
            SessionConfirmationType.CLOSE -> {
                if (flowSessionManager.areAllSessionsInStatuses(checkpoint, waitingFor.sessionIds, CLOSED_STATUSES)) {
                    val receivedEvents = flowSessionManager.getReceivedEvents(checkpoint, waitingFor.sessionIds)
                    if (receivedEvents.any { (_, event) -> event.payload !is SessionClose }) {
                        FlowContinuation.Error(CordaRuntimeException("Unexpected data message when session is closing"))
                    } else {
                        flowSessionManager.acknowledgeReceivedEvents(receivedEvents)
                        FlowContinuation.Run(Unit)
                    }
                } else {
                    FlowContinuation.Continue
                }
            }
            null -> {
                // Shouldn't be possible but the compiler flags it as a warning
                throw FlowProcessingException("Session confirmation type was null")
            }
        }
    }
}