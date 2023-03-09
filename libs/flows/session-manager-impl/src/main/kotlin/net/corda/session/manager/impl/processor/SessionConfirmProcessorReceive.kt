package net.corda.session.manager.impl.processor

import java.time.Instant
import net.corda.data.flow.event.SessionEvent
import net.corda.data.flow.event.session.SessionConfirm
import net.corda.data.flow.state.session.SessionState
import net.corda.session.manager.impl.SessionEventProcessor
import net.corda.session.manager.impl.processor.helper.generateErrorSessionStateFromSessionEvent
import net.corda.utilities.debug
import net.corda.utilities.trace
import org.slf4j.LoggerFactory

/**
 * Process a [SessionConfirm] received from the initiated counterparty in response to a SessionInit which was sent to trigger the session.
 * If state is null return a new error state with queued to the counterparty. This shouldn't happen without developer error.
 * Save any session context properties received from the counterparty into the session state.
 */
class SessionConfirmProcessorReceive(
    private val key: Any,
    private val sessionState: SessionState?,
    private val sessionEvent: SessionEvent,
    private val sessionConfirm: SessionConfirm,
    private val instant: Instant
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
            sessionState.apply {
                counterpartySessionProperties = sessionConfirm.contextSessionProperties
            }

            logger.trace {
                "Received SessionConfirm on key $key with receivedSequenceNum ${sessionEvent.receivedSequenceNum} and outOfOrderSequenceNums " +
                        "${sessionEvent.outOfOrderSequenceNums} for session state: $sessionState"
            }

            return sessionState
        }
    }
}
