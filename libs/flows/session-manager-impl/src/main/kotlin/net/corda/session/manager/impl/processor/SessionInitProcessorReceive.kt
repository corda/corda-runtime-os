package net.corda.session.manager.impl.processor

import net.corda.data.flow.FlowKey
import net.corda.data.flow.event.SessionEvent
import net.corda.data.flow.event.session.SessionInit
import net.corda.data.flow.state.session.SessionProcessState
import net.corda.data.flow.state.session.SessionState
import net.corda.data.flow.state.session.SessionStateType
import net.corda.session.manager.SessionEventResult
import net.corda.session.manager.impl.SessionEventProcessor
import net.corda.session.manager.impl.processor.helper.generateAckRecord
import net.corda.v5.base.util.contextLogger
import net.corda.v5.base.util.debug
import net.corda.v5.base.util.uncheckedCast
import java.time.Instant
import java.util.*

/**
 * Process SessionInit messages.
 * Generate [SessionAck] for the SessionInit and create a new [SessionState]
 * If [SessionState] for the given sessionId is null log the duplicate event.
 */
class SessionInitProcessorReceive(
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

        return getSessionInitInboundResult(sessionInit)
    }

    private fun getSessionInitInboundResult(sessionInit: SessionInit): SessionEventResult {
        val sessionId = sessionEvent.sessionId
        val seqNum = sessionEvent.sequenceNum
        val newSessionState = SessionState(
            sessionId, instant.toEpochMilli(), sessionInit.initiatingIdentity,
            false,
            SessionProcessState(seqNum, mutableListOf(sessionEvent)),
            SessionProcessState(seqNum - 1, mutableListOf()),
            SessionStateType.CONFIRMED
        )
        return SessionEventResult(newSessionState, generateAckRecord(seqNum, sessionId, instant))
    }
}
