package net.corda.session.manager.impl.processor

import net.corda.data.flow.event.MessageDirection
import net.corda.data.flow.event.session.SessionCounterpartyInfoRS
import net.corda.data.flow.event.session.SessionError
import net.corda.data.flow.state.session.SessionStateType
import net.corda.flow.utils.KeyValueStore
import net.corda.flow.utils.emptyKeyValuePairList
import net.corda.session.manager.Constants.Companion.FLOW_PROTOCOL
import net.corda.session.manager.Constants.Companion.FLOW_PROTOCOL_VERSION_USED
import net.corda.test.flow.util.buildSessionEvent
import net.corda.test.flow.util.buildSessionState
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.Instant
class SessionCounterpartyInfoRSProcessorReceiveTest {

    private val sessionProps = KeyValueStore().apply {
        put(FLOW_PROTOCOL, "protocol")
        put(FLOW_PROTOCOL_VERSION_USED, "1")
    }.avro

    @Test
    fun `receiving a SessionCounterpartyInfoRS message with properties stores them in the session state`() {
        val inputState = buildSessionState(
            SessionStateType.CONFIRMED, 0, mutableListOf(), 1, mutableListOf()
        )

        val event = buildSessionEvent(
            MessageDirection.INBOUND,
            "sessionId",
            1,
            SessionCounterpartyInfoRS(),
            contextSessionProps = sessionProps
        )
        val sessionCounterpartyInfoRSProcessorReceived =
            SessionCounterpartyInfoRSProcessorReceive("key", inputState, event, Instant.now())
        val sessionState = sessionCounterpartyInfoRSProcessorReceived.execute()

        val messagesToSend = sessionState.receivedEventsState.undeliveredMessages
        assertThat(messagesToSend).isEmpty()
        assertThat(sessionState.sessionProperties).isEqualTo(sessionProps)
    }

    @Test
    fun `test null state generates a new error state and queues an error to send`() {
        val event = buildSessionEvent(
            MessageDirection.OUTBOUND,
            "sessionId",
            1,
            SessionCounterpartyInfoRS(),
            contextSessionProps = emptyKeyValuePairList()
        )
        val sessionCounterpartyInfoRSProcessorReceived = SessionCounterpartyInfoRSProcessorReceive("key", null, event,  Instant.now())
        val sessionState = sessionCounterpartyInfoRSProcessorReceived.execute()

        val messagesToSend = sessionState.sendEventsState.undeliveredMessages
        assertThat(sessionState.status).isEqualTo(SessionStateType.ERROR)
        assertThat(messagesToSend.size).isEqualTo(1)
        assertThat(messagesToSend.first()!!.payload::class.java).isEqualTo(SessionError::class.java)
    }
}
