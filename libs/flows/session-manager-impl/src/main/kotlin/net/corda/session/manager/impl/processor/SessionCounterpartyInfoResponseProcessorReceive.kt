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
 * Process a session counterparty info response.
 *
 * This should only be sent if a session counterparty info request was sent to the counterparty, so for the session
 * receiving this event the session should exist.
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
            val errorMessage = "Received SessionCounterpartyInfoResponse on key $key for " +
                    "sessionId ${sessionEvent.sessionId} which had null state"
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
