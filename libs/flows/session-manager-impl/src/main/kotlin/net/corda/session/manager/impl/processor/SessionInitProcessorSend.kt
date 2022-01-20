package net.corda.session.manager.impl.processor

import net.corda.data.flow.FlowKey
import net.corda.data.flow.event.SessionEvent
import net.corda.data.flow.event.session.SessionInit
import net.corda.data.flow.state.session.SessionProcessState
import net.corda.data.flow.state.session.SessionState
import net.corda.data.flow.state.session.SessionStateType
import net.corda.session.manager.SessionEventResult
import net.corda.session.manager.impl.SessionEventProcessor
import net.corda.session.manager.impl.processor.helper.generateOutBoundRecord
import net.corda.v5.base.util.contextLogger
import net.corda.v5.base.util.debug
import net.corda.v5.base.util.uncheckedCast
import java.time.Instant
import java.util.*

/**
 * Process SessionInit messages to be sent to a counterparty.
 * Create a new [SessionState]
 * If [SessionState] for the given sessionId is null log the duplicate event.
 */
class SessionInitProcessorSend(
    private val flowKey: FlowKey,
    private val sessionState: SessionState?,
    private val sessionEvent: SessionEvent,
    private val instant: Instant
) : SessionEventProcessor {

    private companion object {
        private val logger = contextLogger()
    }

    override fun execute(): SessionEventResult {
        val sessionInit: SessionInit = uncheckedCast(sessionEvent.payload)
        if (sessionState != null) {
            logger.debug { "Received duplicate SessionInit on key $flowKey for session which was not null: $sessionState" }
            return SessionEventResult(sessionState, null)
        }

        return getSessionInitOutboundResult(sessionInit)
    }

    private fun getSessionInitOutboundResult(sessionInit: SessionInit): SessionEventResult {
        val sessionId = generateSessionId()
        val seqNum = 1
        sessionEvent.sessionId = sessionId
        sessionEvent.sequenceNum = seqNum
        sessionEvent.timestamp = instant.toEpochMilli()
        val newSessionState = SessionState(
            sessionId, instant.toEpochMilli(), sessionInit.initiatingIdentity,
            true,
            SessionProcessState(seqNum-1, mutableListOf()),
            SessionProcessState(seqNum, mutableListOf(sessionEvent)),
            SessionStateType.CREATED
        )

        return SessionEventResult(newSessionState, generateOutBoundRecord(sessionEvent, sessionId))
    }

    /**
     * Random ID for session
     */
    private fun generateSessionId(): String {
        return UUID.randomUUID().toString()
    }
}
