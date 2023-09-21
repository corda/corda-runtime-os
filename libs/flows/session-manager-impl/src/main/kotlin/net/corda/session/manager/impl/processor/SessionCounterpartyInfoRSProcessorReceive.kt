package net.corda.session.manager.impl.processor

import net.corda.data.flow.event.SessionEvent
import net.corda.data.flow.state.session.SessionState
import net.corda.data.flow.state.session.SessionStateType
import net.corda.session.manager.impl.SessionEventProcessor
import net.corda.session.manager.impl.processor.helper.generateErrorSessionStateFromSessionEvent
import net.corda.utilities.debug
import net.corda.utilities.trace
import org.slf4j.LoggerFactory
import java.time.Instant

/**
 * Process a [SessionConfirm] received from the initiated counterparty in response to a SessionInit which was sent to trigger the session.
 * If state is null return a new error state with queued to the counterparty. This shouldn't happen without developer error.
 * Save any session context properties received from the counterparty into the session state.
 */
class SessionCounterpartyInfoResponseProcessorReceive(
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
            sessionState.apply {
                if (status == SessionStateType.CREATED) {
                    status = SessionStateType.CONFIRMED
                }
                // save the common session properties sent by the initiated party, contains requireClose and flow protocol version
                sessionProperties = sessionEvent.contextSessionProperties
            }

            logger.trace {
                "Received SessionCounterpartyInfoResponse on key $key for session state: $sessionState"
            }

            return sessionState
        }
    }
}
