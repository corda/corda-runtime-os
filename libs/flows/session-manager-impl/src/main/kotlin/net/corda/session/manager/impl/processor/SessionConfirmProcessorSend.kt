package net.corda.session.manager.impl.processor

import java.time.Instant
import net.corda.data.flow.event.SessionEvent
import net.corda.data.flow.event.session.SessionConfirm
import net.corda.data.flow.state.session.SessionState
import net.corda.flow.utils.KeyValueStore
import net.corda.session.manager.Constants.Companion.FLOW_PROTOCOL_VERSION_USED
import net.corda.session.manager.impl.SessionEventProcessor
import net.corda.session.manager.impl.processor.helper.generateErrorSessionStateFromSessionEvent
import net.corda.utilities.trace
import org.slf4j.LoggerFactory

/**
 * Process SessionConfirm message to be sent to the initiating counterparty.
 * Populates the session properties with the protocol version the initiated party is using.
 * Hardcodes seqNum to be 1 as this will always be the first message sent from the Initiated party to the Initiating party.
 */
class SessionConfirmProcessorSend(
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
        if (sessionState == null) {
            val errorMessage = "Tried to send sessionConfirm for sessionState which was null. Key: $key, SessionEvent: $sessionEvent"
            logger.warn(errorMessage)
            return generateErrorSessionStateFromSessionEvent(errorMessage, sessionEvent, "sessionConfirm-NullSessionState", instant)
        }

        val newSessionId = sessionEvent.sessionId
        val seqNum = 1

        sessionEvent.apply {
            sequenceNum = seqNum
            timestamp = instant
        }

        val contextSessionPropertiesToSend = KeyValueStore(sessionConfirm.contextSessionProperties)
        val contextSessionProperties = KeyValueStore(sessionState.counterpartySessionProperties)
        contextSessionPropertiesToSend[FLOW_PROTOCOL_VERSION_USED]?.let { contextSessionProperties[FLOW_PROTOCOL_VERSION_USED] = it }

        sessionState.apply {
            counterpartySessionProperties = contextSessionProperties.avro
        }

        logger.trace { "Sending SessionConfirm to session with id $newSessionId. sessionState: $sessionState" }

        return sessionState
    }
}
