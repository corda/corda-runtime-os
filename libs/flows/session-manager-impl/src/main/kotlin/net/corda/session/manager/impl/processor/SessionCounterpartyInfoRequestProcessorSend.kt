package net.corda.session.manager.impl.processor

import net.corda.data.flow.event.SessionEvent
import net.corda.data.flow.state.session.SessionState
import net.corda.session.manager.impl.SessionEventProcessor
import net.corda.utilities.trace
import org.slf4j.LoggerFactory
import java.time.Instant

/**
 * Process a message to request session properties from a counterparty.
 * Message will have no sequence number
 */
class SessionCounterpartyInfoRequestProcessorSend(
    private val sessionState: SessionState,
    private val sessionEvent: SessionEvent,
    private val instant: Instant
) : SessionEventProcessor {

    private companion object {
        private val logger = LoggerFactory.getLogger(this::class.java.enclosingClass)
    }

    override fun execute(): SessionState {
        val newSessionId = sessionEvent.sessionId

        sessionEvent.apply {
            sequenceNum = null
            timestamp = instant
        }

        sessionState.sendEventsState.apply {
            undeliveredMessages = undeliveredMessages.plus(sessionEvent)
        }
        logger.trace { "Sending SessionInit with session id $newSessionId." }

        return sessionState
    }
}
