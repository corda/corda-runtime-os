package net.corda.session.manager.impl.processor

import net.corda.data.flow.event.MessageDirection
import net.corda.data.flow.event.SessionEvent
import net.corda.data.flow.event.session.SessionAck
import net.corda.data.flow.event.session.SessionClose
import net.corda.data.flow.event.session.SessionData
import net.corda.data.flow.event.session.SessionError
import net.corda.data.flow.state.session.SessionStateType
import net.corda.session.manager.impl.buildSessionState
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.Instant

class SessionDataProcessorReceiveTest {

    @Test
    fun testNullState() {
        val sessionEvent = SessionEvent(MessageDirection.INBOUND, Instant.now(), "sessionId", 1, SessionData())
        val result = SessionDataProcessorReceive("key", null, sessionEvent, Instant.now()).execute()
        assertThat(result).isNotNull
        assertThat(result.sendEventsState.undeliveredMessages.size).isEqualTo(1)
        assertThat(result.sendEventsState.undeliveredMessages.first().payload::class.java).isEqualTo(SessionError::class.java)
    }

    @Test
    fun testErrorState() {
        val sessionEvent = SessionEvent(MessageDirection.INBOUND, Instant.now(), "sessionId", 3, SessionData())
        val inputState = buildSessionState(
            SessionStateType.ERROR, 2, mutableListOf(), 0, mutableListOf()
        )

        val result = SessionDataProcessorReceive("key", inputState, sessionEvent, Instant.now()).execute()
        assertThat(result).isNotNull
        assertThat(result.status).isEqualTo(SessionStateType.ERROR)
        assertThat(result.sendEventsState.undeliveredMessages.size).isEqualTo(1)
        assertThat(result.sendEventsState.undeliveredMessages.first().payload::class.java).isEqualTo(SessionError::class.java)
    }

    @Test
    fun testOldSeqNum() {
        val sessionEvent = SessionEvent(MessageDirection.INBOUND, Instant.now(), "sessionId", 2, SessionData())
        val inputState = buildSessionState(
            SessionStateType.CONFIRMED, 2, mutableListOf(), 0, mutableListOf()
        )

        val result = SessionDataProcessorReceive("key", inputState, sessionEvent, Instant.now()).execute()
        assertThat(result).isNotNull
        assertThat(result.status).isEqualTo(SessionStateType.CONFIRMED)

        assertThat(result.sendEventsState.undeliveredMessages.size).isEqualTo(1)
        val outputEvent = result.sendEventsState.undeliveredMessages.first()
        assertThat(outputEvent.payload::class.java).isEqualTo(SessionAck::class.java)
        val sessionAck = outputEvent.payload as SessionAck
        assertThat(sessionAck.sequenceNum).isEqualTo(2)
    }


    @Test
    fun testValidDataMessage() {
        val sessionEvent = SessionEvent(MessageDirection.INBOUND, Instant.now(), "sessionId", 3, SessionData())
        val inputState = buildSessionState(
            SessionStateType.CONFIRMED, 2, mutableListOf(), 0, mutableListOf()
        )

        val result = SessionDataProcessorReceive("key", inputState, sessionEvent, Instant.now()).execute()
        assertThat(result).isNotNull
        assertThat(result.status).isEqualTo(SessionStateType.CONFIRMED)
        assertThat(result.sendEventsState.undeliveredMessages.size).isEqualTo(1)
        val outputEvent = result.sendEventsState.undeliveredMessages.first()
        assertThat(outputEvent.payload::class.java).isEqualTo(SessionAck::class.java)
        val sessionAck = outputEvent.payload as SessionAck
        assertThat(sessionAck.sequenceNum).isEqualTo(3)
    }


    @Test
    fun `Receive data after out of order close received`() {
        val dataEvent = SessionEvent(MessageDirection.INBOUND, Instant.now(), "sessionId", 3, SessionData())
        val closeEvent = SessionEvent(MessageDirection.INBOUND, Instant.now(), "sessionId", 4, SessionClose())
        val inputState = buildSessionState(
            SessionStateType.CLOSING, 2, mutableListOf(closeEvent), 0, mutableListOf()
        )

        val result = SessionDataProcessorReceive("key", inputState, dataEvent, Instant.now()).execute()
        assertThat(result).isNotNull
        assertThat(result.status).isEqualTo(SessionStateType.CLOSING)
        assertThat(result.sendEventsState.undeliveredMessages.size).isEqualTo(1)
        val outputEvent = result.sendEventsState.undeliveredMessages.first()
        assertThat(outputEvent.payload::class.java).isEqualTo(SessionAck::class.java)
        val sessionAck = outputEvent.payload as SessionAck
        assertThat(sessionAck.sequenceNum).isEqualTo(3)
    }

    @Test
    fun `Receive new data after close received`() {
        val dataEvent = SessionEvent(MessageDirection.INBOUND, Instant.now(), "sessionId", 4, SessionData())
        val closeEvent = SessionEvent(MessageDirection.INBOUND, Instant.now(), "sessionId", 3, SessionClose())
        val inputState = buildSessionState(
            SessionStateType.CLOSING, 3, mutableListOf(closeEvent), 0, mutableListOf()
        )

        val result = SessionDataProcessorReceive("key", inputState, dataEvent, Instant.now()).execute()
        assertThat(result).isNotNull
        assertThat(result.status).isEqualTo(SessionStateType.ERROR)
        assertThat(result.sendEventsState.undeliveredMessages.size).isEqualTo(1)
        val outputEvent = result.sendEventsState.undeliveredMessages.first()
        assertThat(outputEvent.payload::class.java).isEqualTo(SessionError::class.java)
    }
}