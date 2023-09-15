package net.corda.session.manager.impl.processor

import net.corda.data.flow.event.SessionEvent
import net.corda.session.manager.impl.SessionEventProcessor
import net.corda.utilities.trace
import org.slf4j.LoggerFactory
import java.time.Instant
import net.corda.data.flow.state.session.SessionState
import net.corda.flow.utils.KeyValueStore
import net.corda.session.manager.Constants.Companion.FLOW_SESSION_IS_INTEROP

/**
 * Process SessionInit messages to be sent to a counterparty.
 * Create a new [SessionState]
 * If [SessionState] for the given sessionId is null log the duplicate event.
 */
class SessionInitProcessorSend(
    private val sessionState: SessionState,
    private val sessionEvent: SessionEvent,
    private val instant: Instant
) : SessionEventProcessor {

    private companion object {
        private val logger = LoggerFactory.getLogger(this::class.java.enclosingClass)
    }

    private val isInteropSessionInit = sessionEvent.contextSessionProperties?.let { properties ->
        KeyValueStore(properties)[FLOW_SESSION_IS_INTEROP]?.equals("true")
    } ?: false

    override fun execute(): SessionState {
        val newSessionId = sessionEvent.sessionId
        val seqNum = 1

        sessionEvent.apply {
            sequenceNum = seqNum
            timestamp = instant
        }

        sessionState.apply {
            isInteropSession = isInteropSessionInit
            sendEventsState.lastProcessedSequenceNum = seqNum
            sendEventsState.undeliveredMessages = sendEventsState.undeliveredMessages.plus(sessionEvent)
        }
        logger.trace { "Sending SessionInit with session id $newSessionId." }

        return sessionState
    }
}
