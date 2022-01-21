package net.corda.session.manager.impl.processor

import net.corda.data.flow.event.SessionEvent
import net.corda.data.flow.event.session.SessionInit
import net.corda.data.flow.state.session.SessionProcessState
import net.corda.data.flow.state.session.SessionState
import net.corda.data.flow.state.session.SessionStateType
import net.corda.session.manager.SessionEventResult
import net.corda.session.manager.impl.SessionEventProcessor
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
    private val key: Any,
    private val sessionEvent: SessionEvent,
    private val instant: Instant
) : SessionEventProcessor {

    private companion object {
        private val logger = contextLogger()
    }

    override fun execute(): SessionEventResult {
        val sessionInit: SessionInit = uncheckedCast(sessionEvent.payload)
        return getSessionInitOutboundResult(sessionInit)
    }

    private fun getSessionInitOutboundResult(sessionInit: SessionInit): SessionEventResult {
        val sessionId = generateSessionId()
        val seqNum = 1
        sessionEvent.sessionId = sessionId
        sessionEvent.sequenceNum = seqNum
        sessionEvent.timestamp = instant.toEpochMilli()

        val newSessionState = SessionState.newBuilder()
            .setSessionId(sessionId)
            .setSessionStartTime(instant.toEpochMilli())
            .setIsInitiator(true)
            .setCounterpartyIdentity(sessionInit.initiatedIdentity)
            .setReceivedEventsState(SessionProcessState(0, mutableListOf()))
            .setSentEventsState(SessionProcessState(seqNum, mutableListOf(sessionEvent)))
            .setStatus(SessionStateType.CREATED)
            .build()

        logger.debug { "Creating new session with id $sessionId on key $key for SessionInit sent. sessionState $newSessionState"}

        return SessionEventResult(newSessionState, listOf(sessionEvent))
    }

    /**
     * Random ID for session
     */
    private fun generateSessionId(): String {
        return UUID.randomUUID().toString()
    }
}
