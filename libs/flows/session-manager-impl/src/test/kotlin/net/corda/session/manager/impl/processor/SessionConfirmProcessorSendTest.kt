package net.corda.session.manager.impl.processor

import java.time.Instant
import net.corda.data.flow.event.MessageDirection
import net.corda.data.flow.event.session.SessionConfirm
import net.corda.data.flow.event.session.SessionError
import net.corda.data.flow.state.session.SessionStateType
import net.corda.flow.utils.KeyValueStore
import net.corda.session.manager.Constants.Companion.FLOW_PROTOCOL
import net.corda.session.manager.Constants.Companion.FLOW_PROTOCOL_VERSION_USED
import net.corda.test.flow.util.buildSessionEvent
import net.corda.test.flow.util.buildSessionState
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class SessionConfirmProcessorSendTest {

    private val sessionProps = KeyValueStore().apply {
        put(FLOW_PROTOCOL, "protocol")
        put(FLOW_PROTOCOL_VERSION_USED, "1")
    }.avro

    @Test
    fun `sending a confirm message adds it to the sendsEvent state`() {
        val inputState = buildSessionState(
            SessionStateType.CONFIRMED, 0, mutableListOf(), 1, mutableListOf()
        )

        val event = buildSessionEvent(MessageDirection.OUTBOUND, "sessionId", 1, SessionConfirm(sessionProps), 1)
        val sessionConfirmProcessorSend = SessionConfirmProcessorSend("key", inputState, event, SessionConfirm(sessionProps), Instant
            .now())
        val sessionState = sessionConfirmProcessorSend.execute()

        val sendEventsState = sessionState.sendEventsState
        val messagesToSend = sendEventsState.undeliveredMessages
        assertThat(messagesToSend.size).isEqualTo(1)
        assertThat(sendEventsState.lastProcessedSequenceNum).isEqualTo(1)
    }

    @Test
    fun `test null state generates a new error state and queues an error to send`() {
        val event = buildSessionEvent(MessageDirection.OUTBOUND, "sessionId", 1, SessionConfirm(sessionProps), 1)
        val sessionConfirmProcessorSend = SessionConfirmProcessorSend("key", null, event, SessionConfirm(sessionProps), Instant
            .now())
        val sessionState = sessionConfirmProcessorSend.execute()

        val messagesToSend = sessionState.sendEventsState.undeliveredMessages
        assertThat(sessionState.status).isEqualTo(SessionStateType.ERROR)
        assertThat(messagesToSend.size).isEqualTo(1)
        assertThat(messagesToSend.first()!!.payload::class.java).isEqualTo(SessionError::class.java)
    }
}
