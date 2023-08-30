package net.corda.session.manager.impl.processor

import net.corda.data.flow.event.MessageDirection
import net.corda.data.flow.event.session.SessionConfirm
import net.corda.data.flow.state.session.SessionStateType
import net.corda.flow.utils.KeyValueStore
import net.corda.session.manager.Constants.Companion.FLOW_PROTOCOL
import net.corda.session.manager.Constants.Companion.FLOW_PROTOCOL_VERSION_USED
import net.corda.test.flow.util.buildSessionEvent
import net.corda.test.flow.util.buildSessionState
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.Instant
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

        val event = buildSessionEvent(
            MessageDirection.OUTBOUND,
            "sessionId",
            1,
            SessionConfirm(),
            contextSessionProps = sessionProps
        )
        val sessionConfirmProcessorSend = SessionConfirmProcessorSend(
            inputState, event, Instant
                .now()
        )
        val sessionState = sessionConfirmProcessorSend.execute()

        val sendEventsState = sessionState.sendEventsState
        val messagesToSend = sendEventsState.undeliveredMessages
        assertThat(messagesToSend.size).isEqualTo(1)
        assertThat(sendEventsState.lastProcessedSequenceNum).isEqualTo(1)
    }
}
