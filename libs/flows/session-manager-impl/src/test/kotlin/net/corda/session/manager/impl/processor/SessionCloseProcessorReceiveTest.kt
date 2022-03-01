package net.corda.session.manager.impl.processor

import net.corda.data.flow.event.MessageDirection
import net.corda.data.flow.event.session.SessionAck
import net.corda.data.flow.event.session.SessionClose
import net.corda.data.flow.event.session.SessionError
import net.corda.data.flow.state.session.SessionStateType
import net.corda.session.manager.impl.buildSessionEvent
import net.corda.session.manager.impl.buildSessionState
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.Instant

class SessionCloseProcessorReceiveTest {

    @Test
    fun testNullState() {
        val sessionEvent = buildSessionEvent(MessageDirection.INBOUND, "sessionId", 3, SessionClose())
        val result = SessionCloseProcessorReceive("key", null, sessionEvent, Instant.now()).execute()
        assertThat(result).isNotNull
        assertThat(result.status).isEqualTo(SessionStateType.ERROR)
        assertThat(result.sendEventsState.undeliveredMessages.size).isEqualTo(1)
        assertThat(result.sendEventsState.undeliveredMessages.first().payload::class.java).isEqualTo(SessionError::class.java)
    }

    @Test
    fun `Receive a duplicate close when status is CLOSING`() {
        val sessionEvent = buildSessionEvent(MessageDirection.INBOUND, "sessionId", 1, SessionClose())
        val inputState = buildSessionState(
            SessionStateType.CLOSING, 2, mutableListOf(sessionEvent), 0, mutableListOf()
        )

        val result = SessionCloseProcessorReceive("key", inputState, sessionEvent, Instant.now()).execute()
        assertThat(result).isNotNull
        assertThat(result.status).isEqualTo(SessionStateType.CLOSING)
        assertThat(result.sendEventsState.undeliveredMessages.size).isEqualTo(1)
        assertThat(result.sendEventsState.undeliveredMessages.first().payload::class.java).isEqualTo(SessionAck::class.java)
    }

    @Test
    fun `Receive a close when status is ERROR`() {
        val sessionEvent = buildSessionEvent(MessageDirection.INBOUND, "sessionId", 1, SessionClose())
        val inputState = buildSessionState(
            SessionStateType.ERROR, 0, mutableListOf(), 0, mutableListOf()
        )

        val result = SessionCloseProcessorReceive("key", inputState, sessionEvent, Instant.now()).execute()
        assertThat(result).isNotNull
        assertThat(result.status).isEqualTo(SessionStateType.ERROR)
        assertThat(result.sendEventsState.undeliveredMessages.size).isEqualTo(1)
        assertThat(result.sendEventsState.undeliveredMessages.first().payload::class.java).isEqualTo(SessionError::class.java)
    }

    @Test
    fun `Receive a close when status is CONFIRMED`() {
        val sessionEvent = buildSessionEvent(MessageDirection.INBOUND, "sessionId", 1, SessionClose())
        val inputState = buildSessionState(
            SessionStateType.CONFIRMED, 0, mutableListOf(), 0, mutableListOf()
        )

        val result = SessionCloseProcessorReceive("key", inputState, sessionEvent, Instant.now()).execute()
        assertThat(result).isNotNull
        assertThat(result.status).isEqualTo(SessionStateType.CLOSING)
        assertThat(result.sendEventsState.undeliveredMessages.size).isEqualTo(1)
        assertThat(result.sendEventsState.undeliveredMessages.first().payload::class.java).isEqualTo(SessionAck::class.java)
    }

    @Test
    fun `Receive a close when status is CREATED`() {
        val sessionEvent = buildSessionEvent(MessageDirection.INBOUND, "sessionId", 1, SessionClose())
        val inputState = buildSessionState(
            SessionStateType.CREATED, 0, mutableListOf(), 0, mutableListOf()
        )

        val result = SessionCloseProcessorReceive("key", inputState, sessionEvent, Instant.now()).execute()
        assertThat(result).isNotNull
        assertThat(result.status).isEqualTo(SessionStateType.CLOSING)
        assertThat(result.sendEventsState.undeliveredMessages.size).isEqualTo(1)
        assertThat(result.sendEventsState.undeliveredMessages.first().payload::class.java).isEqualTo(SessionAck::class.java)
    }

    @Test
    fun `Receive a close when status is CLOSING`() {
        val sessionEvent = buildSessionEvent(MessageDirection.INBOUND, "sessionId", 1, SessionClose())
        val inputState = buildSessionState(
            SessionStateType.CLOSING, 0, mutableListOf(), 0, mutableListOf()
        )

        val result = SessionCloseProcessorReceive("key", inputState, sessionEvent, Instant.now()).execute()
        assertThat(result).isNotNull
        assertThat(result.status).isEqualTo(SessionStateType.CLOSED)
        assertThat(result.sendEventsState.undeliveredMessages.size).isEqualTo(1)
        assertThat(result.sendEventsState.undeliveredMessages.first().payload::class.java).isEqualTo(SessionAck::class.java)
    }

    @Test
    fun `Receive a close when status is CLOSING but some sent messages not acked`() {
        val sessionEvent = buildSessionEvent(MessageDirection.INBOUND, "sessionId-test", 1, SessionClose())
        val inputState = buildSessionState(
            SessionStateType.CLOSING, 0, mutableListOf(), 0, mutableListOf(buildSessionEvent(MessageDirection.OUTBOUND,
                "sessionId-test2", 2,
                SessionClose()))
        )

        val result = SessionCloseProcessorReceive("key", inputState, sessionEvent, Instant.now()).execute()
        assertThat(result).isNotNull
        assertThat(result.status).isEqualTo(SessionStateType.WAIT_FOR_FINAL_ACK)
        assertThat(result.sendEventsState.undeliveredMessages.size).isEqualTo(2)
        assertThat(result.sendEventsState.undeliveredMessages.first().payload::class.java).isEqualTo(SessionClose::class.java)
        assertThat(result.sendEventsState.undeliveredMessages.last().payload::class.java).isEqualTo(SessionAck::class.java)
    }
}