package net.corda.session.manager.impl.processor

import net.corda.data.flow.event.MessageDirection
import net.corda.data.flow.event.SessionEvent
import net.corda.data.flow.event.session.SessionClose
import net.corda.data.flow.event.session.SessionError
import net.corda.data.flow.state.session.SessionStateType
import net.corda.session.manager.impl.buildSessionState
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.Instant

class SessionCloseProcessorSendTest {

    @Test
    fun testNullState() {
        val sessionEvent = SessionEvent(MessageDirection.OUTBOUND, 1, "sessionId", null, SessionClose())
        val result = SessionCloseProcessorSend("key", null, sessionEvent, Instant.now()).execute()
        assertThat(result).isNotNull
        assertThat(result.status).isEqualTo(SessionStateType.ERROR)
        assertThat(result.sentEventsState.undeliveredMessages.size).isEqualTo(1)
        assertThat(result.sentEventsState.undeliveredMessages.first().payload::class.java).isEqualTo(SessionError::class.java)
    }

    @Test
    fun testErrorState() {
        val sessionEvent = SessionEvent(MessageDirection.OUTBOUND, 1, "sessionId", null, SessionClose())
        val inputState = buildSessionState(
            SessionStateType.ERROR, 0, emptyList(), 0, mutableListOf()
        )

        val result = SessionCloseProcessorSend("key", inputState, sessionEvent, Instant.now()).execute()
        assertThat(result).isNotNull
        assertThat(result.status).isEqualTo(SessionStateType.ERROR)
        assertThat(result.sentEventsState.undeliveredMessages.size).isEqualTo(1)
        assertThat(result.sentEventsState.undeliveredMessages.first().payload::class.java).isEqualTo(SessionError::class.java)
    }

    @Test
    fun testSessionMismatchExtraDataMessages() {
        val sessionEvent = SessionEvent(MessageDirection.OUTBOUND, 1, "sessionId", null, SessionClose())
        val inputState = buildSessionState(
            SessionStateType.CONFIRMED, 0, mutableListOf(SessionEvent()), 0, mutableListOf()
        )

        val result = SessionCloseProcessorSend("key", inputState, sessionEvent, Instant.now()).execute()
        assertThat(result).isNotNull
        assertThat(result.status).isEqualTo(SessionStateType.ERROR)
        assertThat(result.sentEventsState.undeliveredMessages.size).isEqualTo(1)
        assertThat(result.sentEventsState.undeliveredMessages.first().payload::class.java).isEqualTo(SessionError::class.java)
    }

    @Test
    fun testConfirmedState() {
        val sessionEvent = SessionEvent(MessageDirection.OUTBOUND, 1, "sessionId", null, SessionClose())
        val inputState = buildSessionState(
            SessionStateType.CONFIRMED, 0, mutableListOf(), 0, mutableListOf()
        )

        val result = SessionCloseProcessorSend("key", inputState, sessionEvent, Instant.now()).execute()
        assertThat(result).isNotNull
        assertThat(result.status).isEqualTo(SessionStateType.CLOSING)
        assertThat(result.sentEventsState.undeliveredMessages.size).isEqualTo(1)
        val sessionEventOutput = result.sentEventsState.undeliveredMessages.first()
        assertThat(sessionEventOutput.sequenceNum).isNotNull
        assertThat(sessionEventOutput.payload).isEqualTo(sessionEvent.payload)
    }

    @Test
    fun testClosingState() {
        val sessionEvent = SessionEvent(MessageDirection.OUTBOUND, 1, "sessionId", null, SessionClose())
        val inputState = buildSessionState(
            SessionStateType.CLOSING, 0, mutableListOf(), 0, mutableListOf()
        )

        val result = SessionCloseProcessorSend("key", inputState, sessionEvent, Instant.now()).execute()
        assertThat(result).isNotNull
        assertThat(result.status).isEqualTo(SessionStateType.WAIT_FOR_FINAL_ACK)
        assertThat(result.sentEventsState.undeliveredMessages.size).isEqualTo(1)
        val sessionEventOutput = result.sentEventsState.undeliveredMessages.first()
        assertThat(sessionEventOutput.sequenceNum).isNotNull
        assertThat(sessionEventOutput.payload).isEqualTo(sessionEvent.payload)
    }
}