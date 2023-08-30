package net.corda.session.manager.impl.processor

import net.corda.data.flow.event.SessionEvent
import net.corda.data.flow.event.session.SessionConfirm
import net.corda.data.flow.state.session.SessionState
import net.corda.data.flow.state.session.SessionStateType
import net.corda.session.manager.impl.SessionEventProcessor
import net.corda.session.manager.impl.processor.helper.generateErrorSessionStateFromSessionEvent
import net.corda.session.manager.impl.processor.helper.recalcHighWatermark
import net.corda.utilities.debug
import net.corda.utilities.trace
import org.slf4j.LoggerFactory
import java.time.Instant

/**
 * Process a [SessionConfirm] received from the initiated counterparty in response to a SessionInit which was sent to trigger the session.
 * If state is null return a new error state with queued to the counterparty. This shouldn't happen without developer error.
 * Save any session context properties received from the counterparty into the session state.
 */
class SessionConfirmProcessorReceive(
    private val key: Any,
    private val sessionState: SessionState?,
    private val sessionEvent: SessionEvent,
    private val instant: Instant,
) : SessionEventProcessor {

    private companion object {
        private val logger = LoggerFactory.getLogger(this::class.java.enclosingClass)
    }

    override fun execute(): SessionState {
        return if (sessionState == null) {
            val errorMessage = "Received SessionConfirm on key $key for sessionId ${sessionEvent.sessionId} which had null state"
            logger.debug { errorMessage }
            generateErrorSessionStateFromSessionEvent(errorMessage, sessionEvent, "SessionConfirm-NullState", instant)
        } else {
            val eventsReceived = sessionState.receivedEventsState.undeliveredMessages.plus(sessionEvent)
                .distinctBy { it.sequenceNum }.sortedBy { it.sequenceNum }

            sessionState.apply {
                if (status == SessionStateType.CREATED) {
                    status = SessionStateType.CONFIRMED
                }
                sessionProperties = sessionEvent.contextSessionProperties
                //recalc high watermark but do not add the session confirm to the undelivered messages
                receivedEventsState.lastProcessedSequenceNum =
                    recalcHighWatermark(eventsReceived, receivedEventsState.lastProcessedSequenceNum)
            }

            logger.trace {
                "Received SessionConfirm on key $key for session state: $sessionState"
            }

            return sessionState
        }
    }
}
