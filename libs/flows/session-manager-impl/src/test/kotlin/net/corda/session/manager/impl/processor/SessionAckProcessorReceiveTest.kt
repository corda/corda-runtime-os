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
    fun `test null state generates a new error state and queues an error to send`() {
        val sessionAckProcessorReceived = SessionAckProcessorReceive("key", null, "sessionId", 1, Instant.now())
        val sessionState = sessionAckProcessorReceived.execute()

        val messagesToSend = sessionState.sendEventsState.undeliveredMessages
        assertThat(sessionState.status).isEqualTo(SessionStateType.ERROR)
        assertThat(messagesToSend.size).isEqualTo(1)
        assertThat(messagesToSend.first()!!.payload::class.java).isEqualTo(SessionError::class.java)
    }

    @Test
    fun `CREATED state, ack received for init message sent, state moves to CONFIRMED`() {
        val sessionState = buildSessionState(
            SessionStateType.CREATED, 0, emptyList(), 1, mutableListOf(SessionEvent(MessageDirection.OUTBOUND, 1, "", 1, SessionInit()))
        )

        val updatedState = SessionAckProcessorReceive("key", sessionState, "sessionId", 1, Instant.now()).execute()

        assertThat(updatedState.status).isEqualTo(SessionStateType.CONFIRMED)
        assertThat(updatedState.sendEventsState?.undeliveredMessages).isEmpty()
    }

    @Test
    fun `CONFIRMED state, ack received for 1 or 2 data events, 1 data is left`() {
        val sessionState = buildSessionState(
            SessionStateType.CONFIRMED, 0, emptyList(), 3, mutableListOf(
                SessionEvent(MessageDirection.OUTBOUND, 1, "", 2, SessionData()),
                SessionEvent(MessageDirection.OUTBOUND, 1, "", 3, SessionData())
            )
        )

        val updatedState = SessionAckProcessorReceive("key", sessionState, "sessionId", 2, Instant.now()).execute()

        assertThat(updatedState.status).isEqualTo(SessionStateType.CONFIRMED)
        assertThat(updatedState.sendEventsState?.undeliveredMessages?.size).isEqualTo(1)
    }

    @Test
    fun `WAIT_FOR_FINAL_ACK state, final ack is received, state moves to CLOSED`() {
        val sessionState = buildSessionState(
            SessionStateType.WAIT_FOR_FINAL_ACK, 0, emptyList(), 3, mutableListOf(SessionEvent(MessageDirection.OUTBOUND, 1, "", 3,
                SessionClose()))
        )

        val updatedState = SessionAckProcessorReceive("key", sessionState, "sessionId", 3, Instant.now()).execute()

        assertThat(updatedState.status).isEqualTo(SessionStateType.CLOSED)
        assertThat(updatedState.sendEventsState?.undeliveredMessages).isEmpty()
    }
}
