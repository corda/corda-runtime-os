package net.corda.session.manager.impl.processor

import net.corda.data.flow.event.MessageDirection
import net.corda.data.flow.event.SessionEvent
import net.corda.data.flow.event.session.SessionClose
import net.corda.data.flow.event.session.SessionError
import net.corda.data.flow.state.session.SessionStateType
import net.corda.test.flow.util.buildSessionEvent
import net.corda.test.flow.util.buildSessionState
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.Instant

class SessionCloseProcessorSendTest {

    @Test
    fun `send close when state is null`() {
        val sessionEvent = buildSessionEvent(MessageDirection.OUTBOUND, "sessionId", 1, SessionClose())

        val result = SessionCloseProcessorSend("key", null, sessionEvent, Instant.now()).execute()
        assertThat(result).isNotNull
        assertThat(result.status).isEqualTo(SessionStateType.ERROR)
        assertThat(result.sendEventsState.undeliveredMessages.size).isEqualTo(1)
        assertThat(result.sendEventsState.undeliveredMessages.first().payload::class.java).isEqualTo(SessionError::class.java)
    }

    @Test
    fun `Send close when status is ERROR`() {
        val sessionEvent = buildSessionEvent(MessageDirection.OUTBOUND, "sessionId", 1, SessionClose())

        val inputState = buildSessionState(
            SessionStateType.ERROR, 0, emptyList(), 0, mutableListOf()
        )

        val result = SessionCloseProcessorSend("key", inputState, sessionEvent, Instant.now()).execute()
        assertThat(result).isNotNull
        assertThat(result.status).isEqualTo(SessionStateType.ERROR)
        assertThat(result.sendEventsState.undeliveredMessages.size).isEqualTo(1)
        assertThat(result.sendEventsState.undeliveredMessages.first().payload::class.java).isEqualTo(SessionError::class.java)
    }

    @Test
    fun `Send close when some received events have not been processed by the client`() {
        val sessionEvent = buildSessionEvent(MessageDirection.OUTBOUND, "sessionId", 1, SessionClose())

        val inputState = buildSessionState(
            SessionStateType.CONFIRMED, 0, mutableListOf(SessionEvent()), 0, mutableListOf()
        )

        val result = SessionCloseProcessorSend("key", inputState, sessionEvent, Instant.now()).execute()
        assertThat(result).isNotNull
        assertThat(result.status).isEqualTo(SessionStateType.ERROR)
        assertThat(result.sendEventsState.undeliveredMessages.size).isEqualTo(1)
        assertThat(result.sendEventsState.undeliveredMessages.first().payload::class.java).isEqualTo(SessionError::class.java)
    }

    @Test
    fun `Send close when status is CONFIRMED`() {
        val sessionEvent = buildSessionEvent(MessageDirection.OUTBOUND, "sessionId", 1, SessionClose())

        val inputState = buildSessionState(
            SessionStateType.CONFIRMED, 0, mutableListOf(), 0, mutableListOf()
        )

        val result = SessionCloseProcessorSend("key", inputState, sessionEvent, Instant.now()).execute()
        assertThat(result).isNotNull
        assertThat(result.status).isEqualTo(SessionStateType.CLOSING)
        assertThat(result.sendEventsState.undeliveredMessages.size).isEqualTo(1)
        val sessionEventOutput = result.sendEventsState.undeliveredMessages.first()
        assertThat(sessionEventOutput.sequenceNum).isNotNull
        assertThat(sessionEventOutput.payload).isEqualTo(sessionEvent.payload)
    }

    @Test
    fun `Send close when status is CREATED`() {
        val sessionEvent = buildSessionEvent(MessageDirection.OUTBOUND, "sessionId", 1, SessionClose())

        val inputState = buildSessionState(
            SessionStateType.CREATED, 0, mutableListOf(), 0, mutableListOf()
        )

        val result = SessionCloseProcessorSend("key", inputState, sessionEvent, Instant.now()).execute()
        assertThat(result).isNotNull
        assertThat(result.status).isEqualTo(SessionStateType.ERROR)
        assertThat(result.sendEventsState.undeliveredMessages.size).isEqualTo(1)
        assertThat(result.sendEventsState.undeliveredMessages.first().payload::class.java).isEqualTo(SessionError::class.java)
    }


    @Test
    fun `Send close when status is CLOSED`() {
        val sessionEvent = buildSessionEvent(MessageDirection.OUTBOUND, "sessionId", 1, SessionClose())

        val inputState = buildSessionState(
            SessionStateType.CLOSED, 0, mutableListOf(), 0, mutableListOf()
        )

        val result = SessionCloseProcessorSend("key", inputState, sessionEvent, Instant.now()).execute()
        assertThat(result).isNotNull
        assertThat(result.status).isEqualTo(SessionStateType.ERROR)
        assertThat(result.sendEventsState.undeliveredMessages.size).isEqualTo(1)
        assertThat(result.sendEventsState.undeliveredMessages.first().payload::class.java).isEqualTo(SessionError::class.java)
    }

    @Test
    fun `Send close when status is already CLOSING due to close received`() {
        val sessionEvent = buildSessionEvent(MessageDirection.OUTBOUND, "sessionId", 1, SessionClose())

        val inputState = buildSessionState(
            SessionStateType.CLOSING, 0, mutableListOf(sessionEvent), 0, mutableListOf()
        )

        val result = SessionCloseProcessorSend("key", inputState, sessionEvent, Instant.now()).execute()
        assertThat(result).isNotNull
        assertThat(result.status).isEqualTo(SessionStateType.WAIT_FOR_FINAL_ACK)
        assertThat(result.sendEventsState.undeliveredMessages.size).isEqualTo(1)
        val sessionEventOutput = result.sendEventsState.undeliveredMessages.first()
        assertThat(sessionEventOutput.sequenceNum).isNotNull
        assertThat(sessionEventOutput.payload).isEqualTo(sessionEvent.payload)
    }


    @Test
    fun `Send close when status is already CLOSING due to close sent`() {
        val sessionEvent = buildSessionEvent(MessageDirection.OUTBOUND, "sessionId", 1, SessionClose())

        val inputState = buildSessionState(
            SessionStateType.CLOSING, 0, mutableListOf(), 0, mutableListOf(sessionEvent)
        )

        val result = SessionCloseProcessorSend("key", inputState, sessionEvent, Instant.now()).execute()
        assertThat(result).isNotNull
        assertThat(result.status).isEqualTo(SessionStateType.ERROR)
        assertThat(result.sendEventsState.undeliveredMessages.size).isEqualTo(1)
        val sessionEventOutput = result.sendEventsState.undeliveredMessages.last()
        assertThat(sessionEventOutput.payload::class.java).isEqualTo(SessionError::class.java)
    }
}
