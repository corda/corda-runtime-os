package net.corda.session.manager.impl.processor

import net.corda.data.flow.event.MessageDirection
import net.corda.data.flow.event.SessionEvent
import net.corda.data.flow.event.session.SessionCounterpartyInfoResponse
import net.corda.data.flow.state.session.SessionState
import net.corda.session.manager.impl.SessionEventProcessor
import net.corda.session.manager.impl.processor.helper.generateErrorSessionStateFromSessionEvent
import net.corda.utilities.debug
import net.corda.utilities.trace
import org.slf4j.LoggerFactory
import java.time.Instant

/**
 * Process a [SessionCounterpartyInfoRequest] received from the initiating counterparty.
 * Send a response event back containing the saved sessionState's sessionProperties initialized upon creation.
 * If state is null return a new error state with queued to the counterparty. This shouldn't happen without developer error.
 */
class SessionCounterpartyInfoRequestProcessorReceive(
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
            val errorMessage = "Received SessionCounterpartyInfoRequest on key $key for " +
                    "sessionId ${sessionEvent.sessionId} which had null state"
            logger.debug { errorMessage }
            generateErrorSessionStateFromSessionEvent(
                errorMessage,
                sessionEvent,
                "SessionCounterpartyInfoRequest-NullState",
                instant
            )
        } else {
            logger.trace {
                "Received SessionCounterpartyInfoRequest on key $key for session state: $sessionState"
            }

            val counterpartyInfoResponse = SessionEvent.newBuilder()
                .setSessionId(sessionState.sessionId)
                .setMessageDirection(MessageDirection.OUTBOUND)
                .setSequenceNum(null)
                .setInitiatingIdentity(sessionEvent.initiatingIdentity)
                .setInitiatedIdentity(sessionEvent.initiatedIdentity)
                .setPayload(SessionCounterpartyInfoResponse())
                .setTimestamp(instant)
                .setContextSessionProperties(sessionState.sessionProperties)
                .build()

            sessionState.sendEventsState.apply {
                undeliveredMessages = undeliveredMessages.plus(counterpartyInfoResponse)
            }

            return sessionState
        }
    }
}
