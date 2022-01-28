package net.corda.session.manager.impl.processor

import net.corda.data.flow.event.MessageDirection
import net.corda.data.flow.event.SessionEvent
import net.corda.data.flow.event.session.SessionClose
import net.corda.data.flow.event.session.SessionData
import net.corda.data.flow.event.session.SessionError
import net.corda.data.flow.event.session.SessionInit
import net.corda.data.flow.state.session.SessionStateType
import net.corda.session.manager.impl.buildSessionState
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.Instant

class SessionAckProcessorReceiveTest {

    @Test
    fun testNullState() {
        val sessionAckProcessorReceived = SessionAckProcessorReceived("key", null, "sessionId", 1, Instant.now())
        val sessionState = sessionAckProcessorReceived.execute()

        val messagesToSend = sessionState.sendEventsState.undeliveredMessages
        assertThat(messagesToSend.size).isEqualTo(1)
        assertThat(messagesToSend.first()!!.payload::class.java).isEqualTo(SessionError::class.java)
    }

    @Test
    fun testInitState() {
        val sessionState = buildSessionState(
            SessionStateType.CREATED, 0, emptyList(), 1, mutableListOf(SessionEvent(MessageDirection.OUTBOUND, 1, "", 1, SessionInit()))
        )

        val updatedState = SessionAckProcessorReceived("key", sessionState, "sessionId", 1, Instant.now()).execute()

        assertThat(updatedState.status).isEqualTo(SessionStateType.CONFIRMED)
        assertThat(updatedState.sendEventsState?.undeliveredMessages).isEmpty()
    }

    @Test
    fun testConfirmedState() {
        val sessionState = buildSessionState(
            SessionStateType.CONFIRMED, 0, emptyList(), 1, mutableListOf(SessionEvent(MessageDirection.OUTBOUND, 1, "", 1, SessionData()))
        )

        val updatedState = SessionAckProcessorReceived("key", sessionState, "sessionId", 1, Instant.now()).execute()

        assertThat(updatedState.status).isEqualTo(SessionStateType.CONFIRMED)
        assertThat(updatedState.sendEventsState?.undeliveredMessages).isEmpty()
    }

    @Test
    fun testWaitFinalAckState() {
        val sessionState = buildSessionState(
            SessionStateType.WAIT_FOR_FINAL_ACK, 0, emptyList(), 3, mutableListOf(SessionEvent(MessageDirection.OUTBOUND, 1, "", 3,
                SessionClose()))
        )

        val updatedState = SessionAckProcessorReceived("key", sessionState, "sessionId", 3, Instant.now()).execute()

        assertThat(updatedState.status).isEqualTo(SessionStateType.CLOSED)
        assertThat(updatedState.sendEventsState?.undeliveredMessages).isEmpty()
    }
}
