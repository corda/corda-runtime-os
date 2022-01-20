package net.corda.session.manager.impl.processor

import net.corda.data.flow.FlowKey
import net.corda.data.flow.event.SessionEvent
import net.corda.data.flow.state.session.SessionState
import net.corda.data.flow.state.session.SessionStateType
import net.corda.session.manager.SessionEventResult
import net.corda.session.manager.impl.SessionEventProcessor
import net.corda.session.manager.impl.processor.helper.generateErrorEvent
import net.corda.session.manager.impl.processor.helper.generateOutBoundRecord
import net.corda.v5.base.util.contextLogger
import java.time.Instant

/**
 * Process a [SessionData] event to be sent to a counterparty.
 * If the current state is not confirmed it indicates a session mismatch bug, return an error message to the counterparty.
 * Set the sequence number of the outbound message and add it to the list of unacked outbound messages
 */
class SessionDataProcessorSend(
    private val flowKey: FlowKey,
    private val sessionState: SessionState?,
    private val sessionEvent: SessionEvent,
    private val instant: Instant
) : SessionEventProcessor {

    private companion object {
        private val logger = contextLogger()
    }

    override fun execute(): SessionEventResult {
        val sessionId = sessionEvent.sessionId

        if (sessionState == null) {
            val errorMessage = "Received SessionData on key $flowKey for sessionId which was null: $sessionId"
            logger.error(errorMessage)
            return SessionEventResult(sessionState, generateErrorEvent(sessionId, errorMessage, "SessionData-NullSessionState", instant))
        }

        return getOutboundDataEventResult(sessionState, sessionId)
    }

    private fun getOutboundDataEventResult(
        sessionState: SessionState,
        sessionId: String
    ): SessionEventResult {
        val currentStatus = sessionState.status

        if (currentStatus != SessionStateType.CONFIRMED) {
            val errorMessage = "Received SessionData on key $flowKey for sessionId with status of : $currentStatus"
            logger.error(errorMessage)
            return SessionEventResult(sessionState, generateErrorEvent(sessionId, errorMessage, "SessionData-InvalidStatus", instant))
        }

        val sentEventState = sessionState.sentEventsState
        val nextSeqNum = sentEventState.lastProcessedSequenceNum + 1
        val undeliveredMessages = sentEventState.undeliveredMessages?.toMutableList() ?: mutableListOf()
        sentEventState.lastProcessedSequenceNum = nextSeqNum
        sessionEvent.sequenceNum = nextSeqNum
        sessionEvent.timestamp = instant.toEpochMilli()
        undeliveredMessages.add(sessionEvent)
        sentEventState.undeliveredMessages = undeliveredMessages
        return SessionEventResult(sessionState, generateOutBoundRecord(sessionEvent, sessionId))
    }
}
