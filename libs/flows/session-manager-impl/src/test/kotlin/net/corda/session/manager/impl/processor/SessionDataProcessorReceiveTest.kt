package net.corda.session.manager.impl.processor

import net.corda.data.flow.event.MessageDirection
import net.corda.data.flow.event.session.SessionClose
import net.corda.data.flow.event.session.SessionData
import net.corda.data.flow.event.session.SessionError
import net.corda.data.flow.state.session.SessionStateType
import net.corda.flow.utils.emptyKeyValuePairList
import net.corda.test.flow.util.buildSessionEvent
import net.corda.test.flow.util.buildSessionState
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.Instant

class SessionDataProcessorReceiveTest {
    
    @Test
    fun testNullState() {
        val sessionEvent = buildSessionEvent(
            MessageDirection.INBOUND, "sessionId", 1, SessionData(), contextSessionProps = emptyKeyValuePairList()
        )

        val result =
            SessionDataProcessorReceive("key", null, sessionEvent, Instant.now()).execute()
        assertThat(result).isNotNull
        assertThat(result.sendEventsState.undeliveredMessages.size).isEqualTo(1)
        assertThat(result.sendEventsState.undeliveredMessages.first().payload::class.java).isEqualTo(SessionError::class.java)
    }

    @Test
    fun testErrorState() {
        val sessionEvent = buildSessionEvent(
            MessageDirection.INBOUND, "sessionId", 3, SessionData(), contextSessionProps = emptyKeyValuePairList()
        )

        val inputState = buildSessionState(
            SessionStateType.ERROR, 2, mutableListOf(), 0, mutableListOf()
        )

        val result = SessionDataProcessorReceive(
            "key", inputState, sessionEvent, Instant.now()
        ).execute()
        assertThat(result).isNotNull
        assertThat(result.status).isEqualTo(SessionStateType.ERROR)
        assertThat(result.sendEventsState.undeliveredMessages.size).isEqualTo(1)
        assertThat(result.sendEventsState.undeliveredMessages.first().payload::class.java).isEqualTo(SessionError::class.java)
    }

    @Test
    fun testOldSeqNum() {
        val sessionEvent = buildSessionEvent(
            MessageDirection.INBOUND, "sessionId", 2, SessionData(), contextSessionProps = emptyKeyValuePairList()
        )

        val inputState = buildSessionState(
            SessionStateType.CONFIRMED, 2, mutableListOf(), 0, mutableListOf()
        )

        val result = SessionDataProcessorReceive(
            "key", inputState, sessionEvent,  Instant.now()
        ).execute()
        assertThat(result).isNotNull
        assertThat(result.status).isEqualTo(SessionStateType.CONFIRMED)

        assertThat(result.sendEventsState.undeliveredMessages).isEmpty()
    }

    @Test
    fun testValidDataMessage() {
        val sessionEvent = buildSessionEvent(
            MessageDirection.INBOUND, "sessionId", 3,  SessionData(), contextSessionProps = emptyKeyValuePairList()
        )

        val inputState = buildSessionState(
            SessionStateType.CONFIRMED, 2, mutableListOf(), 0, mutableListOf()
        )

        val result = SessionDataProcessorReceive(
            "key", inputState, sessionEvent,  Instant.now()
        ).execute()
        assertThat(result).isNotNull
        assertThat(result.status).isEqualTo(SessionStateType.CONFIRMED)
        assertThat(result.sendEventsState.undeliveredMessages).isEmpty()
    }

    @Test
    fun `Receive data after out of order close received`() {
        val dataEvent = buildSessionEvent(
            MessageDirection.INBOUND, "sessionId", 3,  SessionData(), contextSessionProps = emptyKeyValuePairList()
        )
        val closeEvent = buildSessionEvent(
            MessageDirection.INBOUND, "sessionId", 4, SessionClose(), contextSessionProps = emptyKeyValuePairList()
        )

        val inputState = buildSessionState(
            SessionStateType.CLOSING, 2, mutableListOf(closeEvent), 0, mutableListOf()
        )

        val result =
            SessionDataProcessorReceive("key", inputState, dataEvent,  Instant.now()).execute()
        assertThat(result).isNotNull
        assertThat(result.status).isEqualTo(SessionStateType.CLOSING)
        assertThat(result.sendEventsState.undeliveredMessages).isEmpty()
    }


    @Test
    fun `Receive multiple data in order without acknowledging older received messages`() {
        val dataEvent1 = buildSessionEvent(
            MessageDirection.INBOUND, "sessionId", 3, SessionData(), contextSessionProps = emptyKeyValuePairList()
        )
        val dataEvent2 = buildSessionEvent(
            MessageDirection.INBOUND, "sessionId", 4, SessionData(), contextSessionProps = emptyKeyValuePairList()
        )

        val inputState = buildSessionState(
            SessionStateType.CONFIRMED, 3, mutableListOf(dataEvent1), 0, mutableListOf()
        )

        val result =
            SessionDataProcessorReceive("key", inputState, dataEvent2,  Instant.now()).execute()
        assertThat(result).isNotNull
        assertThat(result.status).isEqualTo(SessionStateType.CONFIRMED)
        assertThat(result.receivedEventsState.undeliveredMessages.size).isEqualTo(2)
        assertThat(result.receivedEventsState.lastProcessedSequenceNum).isEqualTo(4)
    }

    @Test
    fun `Receive new data after close received`() {
        val dataEvent = buildSessionEvent(
            MessageDirection.INBOUND, "sessionId", 4,  SessionData(), contextSessionProps = emptyKeyValuePairList()
        )
        val closeEvent = buildSessionEvent(
            MessageDirection.INBOUND, "sessionId", 3, SessionClose(), contextSessionProps = emptyKeyValuePairList()
        )

        val inputState = buildSessionState(
            SessionStateType.CLOSING, 3, mutableListOf(closeEvent), 0, mutableListOf()
        )

        val result =
            SessionDataProcessorReceive("key", inputState, dataEvent,  Instant.now()).execute()
        assertThat(result).isNotNull
        assertThat(result.status).isEqualTo(SessionStateType.ERROR)
        assertThat(result.sendEventsState.undeliveredMessages.size).isEqualTo(1)
        val outputEvent = result.sendEventsState.undeliveredMessages.first()
        assertThat(outputEvent.payload::class.java).isEqualTo(SessionError::class.java)
    }
}
