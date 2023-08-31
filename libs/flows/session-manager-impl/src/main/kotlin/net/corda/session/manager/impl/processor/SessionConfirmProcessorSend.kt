package net.corda.session.manager.impl.processor

import java.time.Instant
import net.corda.data.flow.event.SessionEvent
import net.corda.data.flow.state.session.SessionState
import net.corda.session.manager.impl.SessionEventProcessor
import net.corda.utilities.trace
import org.slf4j.LoggerFactory

/**
 * Process SessionConfirm message to be sent to the initiating counterparty.
 * Populates the session properties with the protocol version the initiated party is using.
 * Hardcodes seqNum to be 1 as this will always be the first message sent from the Initiated party to the Initiating party.
 */
class SessionConfirmProcessorSend(
    private val sessionState: SessionState,
    private val sessionEvent: SessionEvent,
    private val instant: Instant
) : SessionEventProcessor {

    private companion object {
        private val logger = LoggerFactory.getLogger(this::class.java.enclosingClass)
    }

    override fun execute(): SessionState {
        val sessionId = sessionEvent.sessionId
        val seqNum = 1

        sessionEvent.apply {
            sequenceNum = seqNum
            timestamp = instant
        }

        //always the first message sent by the initiated side.
        sessionState.apply {
            sendEventsState.apply {
                undeliveredMessages = undeliveredMessages.plus(sessionEvent)
                lastProcessedSequenceNum = seqNum
            }
        }

        logger.trace { "Sending SessionConfirm to session with id $sessionId. sessionState: $sessionState" }

        return sessionState
    }
}
