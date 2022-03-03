package net.corda.session.manager.impl.processor

import net.corda.data.flow.event.MessageDirection
import net.corda.data.flow.event.session.SessionData
import net.corda.data.flow.event.session.SessionError
import net.corda.data.flow.state.session.SessionStateType
import net.corda.test.flow.util.buildSessionEvent
import net.corda.test.flow.util.buildSessionState
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.Instant

class SessionDataProcessorSendTest {

    @Test
    fun `Send data when state is null`() {
        val sessionEvent = buildSessionEvent(MessageDirection.OUTBOUND, "sessionId", null, SessionData())

        val result = SessionDataProcessorSend("key", null, sessionEvent, Instant.now()).execute()
        assertThat(result).isNotNull
        assertThat(result.status).isEqualTo(SessionStateType.ERROR)
        assertThat(result.sendEventsState.undeliveredMessages.size).isEqualTo(1)
        assertThat(result.sendEventsState.undeliveredMessages.first().payload::class.java).isEqualTo(SessionError::class.java)
    }

    @Test
    fun `Send data when in state ERROR`() {
        val sessionEvent = buildSessionEvent(MessageDirection.OUTBOUND, "sessionId", null, SessionData())

        val inputState = buildSessionState(
            SessionStateType.ERROR, 0, mutableListOf(), 0, mutableListOf()
        )

        val result = SessionDataProcessorSend("key", inputState, sessionEvent, Instant.now()).execute()
        assertThat(result).isNotNull
        assertThat(result.status).isEqualTo(SessionStateType.ERROR)
        assertThat(result.sendEventsState.undeliveredMessages).isEmpty()
    }

    @Test
    fun `Send data when in state CLOSING results in error`() {
        val sessionEvent = buildSessionEvent(MessageDirection.OUTBOUND, "sessionId", null, SessionData())

        val inputState = buildSessionState(
            SessionStateType.CLOSING, 0, mutableListOf(), 0, mutableListOf()
        )

        val result = SessionDataProcessorSend("key", inputState, sessionEvent, Instant.now()).execute()
        assertThat(result).isNotNull
        assertThat(result.status).isEqualTo(SessionStateType.ERROR)
        assertThat(result.sendEventsState.undeliveredMessages.size).isEqualTo(1)
        assertThat(result.sendEventsState.undeliveredMessages.first().payload::class.java).isEqualTo(SessionError::class.java)
    }


    @Test
    fun `Send data when in state CREATED results in error`() {
        val sessionEvent = buildSessionEvent(MessageDirection.OUTBOUND, "sessionId", null, SessionData())
        val inputState = buildSessionState(
            SessionStateType.CREATED, 0, mutableListOf(), 0, mutableListOf()
        )

        val result = SessionDataProcessorSend("key", inputState, sessionEvent, Instant.now()).execute()
        assertThat(result).isNotNull
        assertThat(result.status).isEqualTo(SessionStateType.ERROR)
        assertThat(result.sendEventsState.undeliveredMessages.size).isEqualTo(1)
        assertThat(result.sendEventsState.undeliveredMessages.first().payload::class.java).isEqualTo(SessionError::class.java)
    }

    @Test
    fun `Send data when state is CONFIRMED`() {
        val sessionEvent = buildSessionEvent(MessageDirection.OUTBOUND, "sessionId", null, SessionData())
        val inputState = buildSessionState(
            SessionStateType.CONFIRMED, 0, mutableListOf(), 0, mutableListOf()
        )

        val result = SessionDataProcessorSend("key", inputState, sessionEvent, Instant.now()).execute()
        assertThat(result).isNotNull
        assertThat(result.status).isEqualTo(SessionStateType.CONFIRMED)
        assertThat(result.sendEventsState.undeliveredMessages.size).isEqualTo(1)
        val sessionEventOutput = result.sendEventsState.undeliveredMessages.first()
        assertThat(sessionEventOutput.sequenceNum).isNotNull
        assertThat(sessionEventOutput.payload).isEqualTo(sessionEvent.payload)
    }
}
