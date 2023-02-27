package net.corda.session.manager.impl.processor

import net.corda.data.ExceptionEnvelope
import net.corda.data.flow.event.SessionEvent
import net.corda.data.flow.state.session.SessionState
import net.corda.data.flow.state.session.SessionStateType
import net.corda.session.manager.impl.SessionEventProcessor
import net.corda.session.manager.impl.processor.helper.generateErrorSessionStateFromSessionEvent
import net.corda.utilities.debug
import org.slf4j.LoggerFactory
import java.time.Instant

/**
 * Process a [SessionError] to be sent to a counterparty.
 * If the current state is already in state of ERROR log the error message.
 * If the state is not ERROR update the state to status ERROR.
 */
class SessionErrorProcessorSend(
    private val key: Any,
    private val sessionState: SessionState?,
    private val sessionEvent: SessionEvent,
    private val exceptionEnvelope: ExceptionEnvelope,
    private val instant: Instant,
) : SessionEventProcessor {

    private companion object {
        private val logger = LoggerFactory.getLogger(this::class.java.enclosingClass)
    }

    override fun execute(): SessionState {
        val sessionId = sessionEvent.sessionId
        return if (sessionState == null) {
            val errorMessage = "Tried to send SessionError on key $key for sessionId which had null state: $sessionId. " +
                    "Error message was: $exceptionEnvelope"
            logger.warn(errorMessage)
            generateErrorSessionStateFromSessionEvent(errorMessage, sessionEvent, "SessionData-NullSessionState", instant)
        } else {
            logger.debug {
                "Sending Session Error on sessionId $sessionId. " +
                        "Updating status from ${sessionState.status} to ${SessionStateType.ERROR}. Error message: $exceptionEnvelope"
            }

            sessionEvent.sequenceNum = null

            sessionState.apply {
                status = SessionStateType.ERROR
                sendEventsState.undeliveredMessages = sessionState.sendEventsState.undeliveredMessages.plus(sessionEvent)
            }
        }
    }
}
