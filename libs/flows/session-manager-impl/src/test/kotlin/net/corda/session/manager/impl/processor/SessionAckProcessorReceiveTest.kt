package net.corda.session.manager.impl.processor

import net.corda.data.flow.event.MessageDirection
import net.corda.data.flow.event.session.SessionAck
import net.corda.data.flow.event.session.SessionError
import net.corda.data.flow.state.session.SessionStateType
import net.corda.session.manager.impl.buildSessionEvent
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.Instant

class SessionAckProcessorReceiveTest {

    @Test
    fun `test null state generates a new error state and queues an error to send`() {
        val event = buildSessionEvent(MessageDirection.OUTBOUND, "sessionId", 1, SessionAck(), 1)
        val sessionAckProcessorReceived = SessionAckProcessorReceive("key", null, event, Instant.now())
        val sessionState = sessionAckProcessorReceived.execute()

        val messagesToSend = sessionState.sendEventsState.undeliveredMessages
        assertThat(sessionState.status).isEqualTo(SessionStateType.ERROR)
        assertThat(messagesToSend.size).isEqualTo(1)
        assertThat(messagesToSend.first()!!.payload::class.java).isEqualTo(SessionError::class.java)
    }
}
