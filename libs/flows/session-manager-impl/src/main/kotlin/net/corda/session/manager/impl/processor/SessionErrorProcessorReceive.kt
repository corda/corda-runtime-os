package net.corda.session.manager.impl.processor

import java.time.Instant
import net.corda.data.ExceptionEnvelope
import net.corda.data.flow.event.SessionEvent
import net.corda.data.flow.state.session.SessionState
import net.corda.data.flow.state.session.SessionStateType
import net.corda.session.manager.impl.SessionEventProcessor
import net.corda.session.manager.impl.processor.helper.generateErrorSessionStateFromSessionEvent
import net.corda.v5.base.util.contextLogger
import net.corda.v5.base.util.debug

/**
 * Process a [SessionError] received from a counterparty.
 * If the current state is already in state of ERROR log the error message.
 * If the state is not ERROR update the state to status ERROR.
 */
class SessionErrorProcessorReceive(
    private val key: Any,
    private val sessionState: SessionState?,
    private val sessionEvent: SessionEvent,
    private val exceptionEnvelope: ExceptionEnvelope,
    private val instant: Instant,
) : SessionEventProcessor {

    private companion object {
        private val logger = contextLogger()
    }

    override fun execute(): SessionState {
        val sessionId = sessionEvent.sessionId

        return if (sessionState == null) {
            val errorMessage = "Received SessionError on key $key for sessionId which had null state: $sessionId. " +
                    "Error message received was: $exceptionEnvelope"
            logger.debug { errorMessage }
            generateErrorSessionStateFromSessionEvent(errorMessage, sessionEvent, "SessionData-NullSessionState", instant)
        } else {
            logger.warn(
                "Session Error received on sessionId $sessionId. " +
                        "Updating status from ${sessionState.status} to ${SessionStateType.ERROR}. Error message: $exceptionEnvelope"
            )
            sessionState.apply {
                status = SessionStateType.ERROR
            }
        }
    }
}
