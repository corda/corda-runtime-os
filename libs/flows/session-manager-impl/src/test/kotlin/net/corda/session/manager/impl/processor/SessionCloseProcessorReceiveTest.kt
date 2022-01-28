package net.corda.session.manager.impl.processor

import net.corda.data.flow.event.MessageDirection
import net.corda.data.flow.event.SessionEvent
import net.corda.data.flow.event.session.SessionAck
import net.corda.data.flow.event.session.SessionClose
import net.corda.data.flow.event.session.SessionError
import net.corda.data.flow.state.session.SessionStateType
import net.corda.session.manager.impl.buildSessionState
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.Instant

class SessionCloseProcessorReceiveTest {

    @Test
    fun testNullState() {
        val sessionEvent = SessionEvent(MessageDirection.INBOUND, 1, "sessionId", 3, SessionClose())
        val result = SessionCloseProcessorReceive("key", null, sessionEvent, Instant.now()).execute()
        assertThat(result).isNotNull
        assertThat(result.status).isEqualTo(SessionStateType.ERROR)
        assertThat(result.sendEventsState.undeliveredMessages.size).isEqualTo(1)
        assertThat(result.sendEventsState.undeliveredMessages.first().payload::class.java).isEqualTo(SessionError::class.java)
    }

    @Test
    fun testOldSeqNum() {
        val sessionEvent = SessionEvent(MessageDirection.INBOUND, 1, "sessionId", 1, SessionClose())
        val inputState = buildSessionState(
            SessionStateType.CLOSING, 2, emptyList(), 0, emptyList()
        )

        val result = SessionCloseProcessorReceive("key", inputState, sessionEvent, Instant.now()).execute()
        assertThat(result).isNotNull
        assertThat(result.status).isEqualTo(SessionStateType.CLOSING)
        assertThat(result.sendEventsState.undeliveredMessages.size).isEqualTo(1)
        assertThat(result.sendEventsState.undeliveredMessages.first().payload::class.java).isEqualTo(SessionAck::class.java)
    }

    @Test
    fun testErrorState() {
        val sessionEvent = SessionEvent(MessageDirection.INBOUND, 1, "sessionId", 1, SessionClose())
        val inputState = buildSessionState(
            SessionStateType.ERROR, 0, emptyList(), 0, emptyList()
        )

        val result = SessionCloseProcessorReceive("key", inputState, sessionEvent, Instant.now()).execute()
        assertThat(result).isNotNull
        assertThat(result.status).isEqualTo(SessionStateType.ERROR)
        assertThat(result.sendEventsState.undeliveredMessages.size).isEqualTo(1)
        assertThat(result.sendEventsState.undeliveredMessages.first().payload::class.java).isEqualTo(SessionError::class.java)
    }

    @Test
    fun testConfirmedState() {
        val sessionEvent = SessionEvent(MessageDirection.INBOUND, 1, "sessionId", 1, SessionClose())
        val inputState = buildSessionState(
            SessionStateType.CONFIRMED, 0, emptyList(), 0, emptyList()
        )

        val result = SessionCloseProcessorReceive("key", inputState, sessionEvent, Instant.now()).execute()
        assertThat(result).isNotNull
        assertThat(result.status).isEqualTo(SessionStateType.CLOSING)
        assertThat(result.sendEventsState.undeliveredMessages.size).isEqualTo(1)
        assertThat(result.sendEventsState.undeliveredMessages.first().payload::class.java).isEqualTo(SessionAck::class.java)
    }

    @Test
    fun testClosingState() {
        val sessionEvent = SessionEvent(MessageDirection.INBOUND, 1, "sessionId", 1, SessionClose())
        val inputState = buildSessionState(
            SessionStateType.CLOSING, 0, emptyList(), 0, emptyList()
        )

        val result = SessionCloseProcessorReceive("key", inputState, sessionEvent, Instant.now()).execute()
        assertThat(result).isNotNull
        assertThat(result.status).isEqualTo(SessionStateType.CLOSED)
        assertThat(result.sendEventsState.undeliveredMessages.size).isEqualTo(1)
        assertThat(result.sendEventsState.undeliveredMessages.first().payload::class.java).isEqualTo(SessionAck::class.java)
    }

    @Test
    fun testClosingStateUnackedMessages() {
        val sessionEvent = SessionEvent(MessageDirection.INBOUND, 1, "sessionId", 1, SessionClose())
        val inputState = buildSessionState(
            SessionStateType.CLOSING, 0, emptyList(), 0, mutableListOf(SessionEvent())
        )

        val result = SessionCloseProcessorReceive("key", inputState, sessionEvent, Instant.now()).execute()
        assertThat(result).isNotNull
        assertThat(result.status).isEqualTo(SessionStateType.WAIT_FOR_FINAL_ACK)
        assertThat(result.sendEventsState.undeliveredMessages.size).isEqualTo(2)
        assertThat(result.sendEventsState.undeliveredMessages.last().payload::class.java).isEqualTo(SessionAck::class.java)
    }
}