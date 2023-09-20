package net.corda.session.manager.impl.processor

import net.corda.data.flow.event.MessageDirection
import net.corda.data.flow.event.session.SessionCounterpartyInfoRQ
import net.corda.data.flow.event.session.SessionCounterpartyInfoRS
import net.corda.data.flow.event.session.SessionError
import net.corda.data.flow.event.session.SessionInit
import net.corda.data.flow.state.session.SessionStateType
import net.corda.flow.utils.KeyValueStore
import net.corda.flow.utils.emptyKeyValuePairList
import net.corda.session.manager.Constants.Companion.FLOW_PROTOCOL
import net.corda.session.manager.Constants.Companion.FLOW_PROTOCOL_VERSION_USED
import net.corda.test.flow.util.buildSessionEvent
import net.corda.test.flow.util.buildSessionState
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Instant
class SessionCounterpartyInfoRQProcessorReceiveTest {

    private val sessionProps = KeyValueStore().apply {
        put(FLOW_PROTOCOL, "protocol")
        put(FLOW_PROTOCOL_VERSION_USED, "1")
    }.avro

    @Test
    fun `receiving a SessionCounterpartyInfoRQ message responds with a SessionCounterpartyInfoRS`() {
        val inputState = buildSessionState(
            SessionStateType.CONFIRMED, 0, mutableListOf(), 1, mutableListOf(), sessionProperties = sessionProps
        )

        val event = buildSessionEvent(
            MessageDirection.INBOUND,
            "sessionId",
            1,
            SessionCounterpartyInfoRQ(SessionInit()),
            contextSessionProps = emptyKeyValuePairList()
        )
        val sessionCounterpartyInfoRQProcessorReceived =
            SessionCounterpartyInfoRQProcessorReceive("key", inputState, event, Instant.now())
        val sessionState = sessionCounterpartyInfoRQProcessorReceived.execute()

        val messagesToSend = sessionState.sendEventsState.undeliveredMessages
        assertThat(messagesToSend).size().isEqualTo(1)
        val message = messagesToSend.first()
        assertTrue(message.sequenceNum == null)
        assertThat(message.payload::class.java).isEqualTo(SessionCounterpartyInfoRS::class.java)
        assertThat(sessionState.sessionProperties).isEqualTo(sessionProps)
    }

    @Test
    fun `test null state generates a new error state and queues an error to send`() {
        val event = buildSessionEvent(
            MessageDirection.OUTBOUND,
            "sessionId",
            1,
            SessionCounterpartyInfoRQ(SessionInit()),
            contextSessionProps = emptyKeyValuePairList()
        )
        val sessionCounterpartyInfoRQProcessorReceive = SessionCounterpartyInfoRQProcessorReceive("key", null, event,  Instant.now())
        val sessionState = sessionCounterpartyInfoRQProcessorReceive.execute()

        val messagesToSend = sessionState.sendEventsState.undeliveredMessages
        assertThat(sessionState.status).isEqualTo(SessionStateType.ERROR)
        assertThat(messagesToSend.size).isEqualTo(1)
        assertThat(messagesToSend.first()!!.payload::class.java).isEqualTo(SessionError::class.java)
    }
}
