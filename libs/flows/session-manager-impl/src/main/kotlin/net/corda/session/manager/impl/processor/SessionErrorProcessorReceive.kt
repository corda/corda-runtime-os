package net.corda.session.manager.impl.processor

import net.corda.data.ExceptionEnvelope
import net.corda.data.flow.FlowKey
import net.corda.data.flow.event.SessionEvent
import net.corda.data.flow.state.session.SessionState
import net.corda.data.flow.state.session.SessionStateType
import net.corda.session.manager.SessionEventResult
import net.corda.session.manager.impl.SessionEventProcessor
import net.corda.session.manager.impl.processor.helper.generateErrorEvent
import net.corda.v5.base.util.contextLogger
import java.time.Instant

/**
 * Process a [SessionError] received from a counterparty. I
 * f the current state is already in state of ERROR log the error message.
 * If the state is not ERROR update the state to status ERROR.
 */
class SessionErrorProcessorReceive(
    private val flowKey: FlowKey,
    private val sessionState: SessionState?,
    private val sessionEvent: SessionEvent,
    private val exceptionEnvelope: ExceptionEnvelope,
    private val instant: Instant,
) : SessionEventProcessor {

    private companion object {
        private val logger = contextLogger()
    }

    override fun execute(): SessionEventResult {
        val sessionId = sessionEvent.sessionId

        return if (sessionState == null) {
            val errorMessage = "Received SessionError on key $flowKey for sessionId which had null state: $sessionId. " +
                    "Error message received was: $exceptionEnvelope"
            logger.error(errorMessage)
            SessionEventResult(sessionState,  generateErrorEvent(sessionId, errorMessage, "SessionData-NullSessionState", instant))
        } else {
            if (sessionState.status == SessionStateType.ERROR) {
                logger.info(
                    "Session Error received on sessionId $sessionId. " +
                            "Status was already ${SessionStateType.ERROR}. Error message: $exceptionEnvelope"
                )
            } else {
                //should this be info or error?
                logger.error(
                    "Session Error received on sessionId $sessionId. " +
                            "Updating status from ${sessionState.status} to ${SessionStateType.ERROR}. Error message: $exceptionEnvelope"
                )
                sessionState.status = SessionStateType.ERROR
            }
            SessionEventResult(sessionState, null)
        }
    }
}
