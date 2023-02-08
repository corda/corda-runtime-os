package net.corda.flow.pipeline.handlers.waiting.sessions

import net.corda.data.flow.event.session.SessionClose
import net.corda.data.flow.state.session.SessionStateType
import net.corda.data.flow.state.waiting.SessionConfirmation
import net.corda.data.flow.state.waiting.SessionConfirmationType
import net.corda.flow.fiber.FlowContinuation
import net.corda.flow.pipeline.FlowEventContext
import net.corda.flow.pipeline.exceptions.FlowFatalException
import net.corda.flow.pipeline.handlers.waiting.FlowWaitingForHandler
import net.corda.flow.pipeline.sessions.FlowSessionManager
import net.corda.flow.pipeline.sessions.FlowSessionStateException
import net.corda.flow.state.FlowCheckpoint
import net.corda.v5.base.exceptions.CordaRuntimeException
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference

@Component(service = [FlowWaitingForHandler::class])
class SessionConfirmationWaitingForHandler @Activate constructor(
    @Reference(service = FlowSessionManager::class)
    private val flowSessionManager: FlowSessionManager
) : FlowWaitingForHandler<SessionConfirmation> {

    override val type = SessionConfirmation::class.java

    override fun runOrContinue(context: FlowEventContext<*>, waitingFor: SessionConfirmation): FlowContinuation {
        return try {
            when (waitingFor.type) {
                SessionConfirmationType.INITIATE -> waitingForSessionInit(context, waitingFor)
                SessionConfirmationType.CLOSE -> waitingForSessionsToClose(context, waitingFor)
                null -> {
                    // Shouldn't be possible but the compiler flags it as a warning
                    throw FlowFatalException("Session confirmation type was null")
                }
            }
        } catch (e: FlowSessionStateException) {
            // TODO CORE-4850 Wakeup with error when session does not exist
            throw FlowFatalException(e.message, e)
        }
    }

    private fun waitingForSessionInit(context: FlowEventContext<*>, waitingFor: SessionConfirmation): FlowContinuation {
        val checkpoint = context.checkpoint
        val erroredSessions = flowSessionManager.getSessionsWithStatus(
            checkpoint,
            waitingFor.sessionIds,
            SessionStateType.ERROR
        ).map { it.sessionId }

        return when {
            erroredSessions.isNotEmpty() -> {
                FlowContinuation.Error(CordaRuntimeException("Failed to initiate sessions: $erroredSessions"))
            }
            areAllSessionsConfirmed(checkpoint, waitingFor) -> {
                FlowContinuation.Run(Unit)
            }
            else -> {
                FlowContinuation.Continue
            }
        }
    }

    private fun areAllSessionsConfirmed(checkpoint: FlowCheckpoint, waitingFor: SessionConfirmation): Boolean {
        return flowSessionManager.doAllSessionsHaveStatusIn(
            checkpoint,
            waitingFor.sessionIds,
            listOf(SessionStateType.CONFIRMED, SessionStateType.CLOSING)
        )
    }

    private fun waitingForSessionsToClose(
        context: FlowEventContext<*>,
        waitingFor: SessionConfirmation
    ): FlowContinuation {
        val checkpoint = context.checkpoint
        val erroredSessions = flowSessionManager.getSessionsWithStatus(
            checkpoint,
            waitingFor.sessionIds,
            SessionStateType.ERROR
        ).map { it.sessionId }
        return when {
            erroredSessions.isNotEmpty() -> {
                sessionsClosedWithErrorsContinuation(checkpoint, waitingFor, erroredSessions)
            }
            areAllSessionsClosed(checkpoint, waitingFor) -> {
                sessionsClosedWithoutErrorsContinuation(checkpoint, waitingFor)
            }
            else -> FlowContinuation.Continue
        }
    }

    private fun sessionsClosedWithErrorsContinuation(
        checkpoint: FlowCheckpoint,
        waitingFor: SessionConfirmation,
        erroredSessions: List<String>
    ): FlowContinuation {
        return if (areAllSessionsClosedOrErrored(checkpoint, waitingFor, erroredSessions)) {
            flowSessionManager.acknowledgeReceivedEvents(
                flowSessionManager.getReceivedEvents(
                    checkpoint,
                    waitingFor.sessionIds
                )
            )
            FlowContinuation.Error(
                CordaRuntimeException(
                    "Failed to close due to errors from sessions: $erroredSessions. $PROTOCOL_MISMATCH_HINT"
                )
            )
        } else {
            FlowContinuation.Continue
        }
    }

    private fun sessionsClosedWithoutErrorsContinuation(
        checkpoint: FlowCheckpoint,
        waitingFor: SessionConfirmation
    ): FlowContinuation {
        val receivedEvents = flowSessionManager.getReceivedEvents(checkpoint, waitingFor.sessionIds)
        return if (receivedEvents.any { (_, event) -> event.payload !is SessionClose }) {
            FlowContinuation.Error(
                CordaRuntimeException(
                    "Failed to close due to unexpected data message when closing sessions: " +
                            "${waitingFor.sessionIds.toList()}"
                )
            )
        } else {
            flowSessionManager.acknowledgeReceivedEvents(receivedEvents)
            FlowContinuation.Run(Unit)
        }
    }

    private fun areAllSessionsClosedOrErrored(
        checkpoint: FlowCheckpoint,
        waitingFor: SessionConfirmation,
        erroredSessions: List<String>
    ): Boolean {
        val possiblyClosedSessions = waitingFor.sessionIds - erroredSessions
        val closedSessions =
            flowSessionManager.getSessionsWithStatus(checkpoint, possiblyClosedSessions, SessionStateType.CLOSED)
        return waitingFor.sessionIds.toSet() == (closedSessions.map { it.sessionId } + erroredSessions).toSet()
    }

    private fun areAllSessionsClosed(checkpoint: FlowCheckpoint, waitingFor: SessionConfirmation): Boolean {
        return flowSessionManager.doAllSessionsHaveStatus(checkpoint, waitingFor.sessionIds, SessionStateType.CLOSED)
    }
}