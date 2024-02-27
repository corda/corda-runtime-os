package net.corda.session.manager.impl.processor

import net.corda.data.flow.event.MessageDirection
import net.corda.data.flow.event.SessionEvent
import net.corda.data.flow.event.session.SessionClose
import net.corda.data.flow.event.session.SessionError
import net.corda.data.flow.state.session.SessionStateType
import net.corda.data.identity.HoldingIdentity
import net.corda.flow.utils.INITIATED_SESSION_ID_SUFFIX
import net.corda.flow.utils.KeyValueStore
import net.corda.flow.utils.emptyKeyValuePairList
import net.corda.session.manager.Constants
import net.corda.test.flow.util.buildSessionEvent
import net.corda.test.flow.util.buildSessionState
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.Instant

class SessionCloseProcessorSendTest {
    companion object {
        val SESSION_PROPERTIES = KeyValueStore().apply {
            put(Constants.FLOW_SESSION_REQUIRE_CLOSE, true.toString())
        }.avro
    }

    @Test
    fun `Send close when status is ERROR`() {
        val sessionEvent = buildSessionEvent(
            MessageDirection.OUTBOUND,
            "sessionId",
            1,
            SessionClose(),
            contextSessionProps = emptyKeyValuePairList()
        )

        val inputState = buildSessionState(
            SessionStateType.ERROR, 0, emptyList(), 0, mutableListOf(),
            sessionProperties = SESSION_PROPERTIES
        )

        val result = SessionCloseProcessorSend("key", inputState, sessionEvent, Instant.now()).execute()
        assertThat(result).isNotNull
        assertThat(result.status).isEqualTo(SessionStateType.ERROR)
        assertThat(result.sendEventsState.undeliveredMessages.size).isEqualTo(1)
        assertThat(result.sendEventsState.undeliveredMessages.first().payload::class.java).isEqualTo(SessionError::class.java)
    }

    @Test
    fun `Send close when some received events have not been processed by the client`() {
        val sessionEvent = buildSessionEvent(
            MessageDirection.OUTBOUND,
            "sessionId",
            1,
            SessionClose(),
            contextSessionProps = emptyKeyValuePairList()
        )

        val inputState = buildSessionState(
            SessionStateType.CONFIRMED, 0, mutableListOf(SessionEvent()), 0, mutableListOf(),
            sessionProperties = SESSION_PROPERTIES
        )

        val result = SessionCloseProcessorSend("key", inputState, sessionEvent, Instant.now()).execute()
        assertThat(result).isNotNull
        assertThat(result.status).isEqualTo(SessionStateType.ERROR)
        assertThat(result.sendEventsState.undeliveredMessages.size).isEqualTo(1)
        assertThat(result.sendEventsState.undeliveredMessages.first().payload::class.java).isEqualTo(SessionError::class.java)
    }

    @Test
    fun `Send close when status is CONFIRMED`() {
        val sessionEvent = buildSessionEvent(
            MessageDirection.OUTBOUND,
            "sessionId-$INITIATED_SESSION_ID_SUFFIX",
            1,
            SessionClose(),
            contextSessionProps = emptyKeyValuePairList()
        )

        val inputState = buildSessionState(
            SessionStateType.CONFIRMED,
            0,
            mutableListOf(),
            0,
            mutableListOf(),
            sessionStartTime = Instant.now(),
            sessionId = "sessionId-$INITIATED_SESSION_ID_SUFFIX",
            counterpartyIdentity = HoldingIdentity("Alice", "group1"),
            sessionProperties = SESSION_PROPERTIES
        )

        val result = SessionCloseProcessorSend("key", inputState, sessionEvent, Instant.now()).execute()
        assertThat(result).isNotNull
        assertThat(result.status).isEqualTo(SessionStateType.CLOSED)
        assertThat(result.sendEventsState.undeliveredMessages.size).isEqualTo(1)
        val sessionEventOutput = result.sendEventsState.undeliveredMessages.first()
        assertThat(sessionEventOutput.sequenceNum).isNotNull
        assertThat(sessionEventOutput.payload).isEqualTo(sessionEvent.payload)
    }

    @Test
    fun `Send close when status is CREATED`() {
        val sessionEvent = buildSessionEvent(
            MessageDirection.OUTBOUND,
            "sessionId",
            1,
            SessionClose(),
            contextSessionProps = emptyKeyValuePairList()
        )

        val inputState = buildSessionState(
            SessionStateType.CREATED, 0, mutableListOf(), 0, mutableListOf(),
            sessionProperties = SESSION_PROPERTIES
        )

        val result = SessionCloseProcessorSend("key", inputState, sessionEvent, Instant.now()).execute()
        assertThat(result).isNotNull
        assertThat(result.status).isEqualTo(SessionStateType.ERROR)
        assertThat(result.sendEventsState.undeliveredMessages.size).isEqualTo(1)
        assertThat(result.sendEventsState.undeliveredMessages.first().payload::class.java).isEqualTo(SessionError::class.java)
    }

    @Test
    fun `Send close when status is CLOSED does not modify session state`() {
        val sessionEvent = buildSessionEvent(
            MessageDirection.OUTBOUND,
            "sessionId",
            1,
            SessionClose(),
            contextSessionProps = emptyKeyValuePairList()
        )

        val inputState = buildSessionState(
            SessionStateType.CLOSED, 0, mutableListOf(), 0, mutableListOf(),
            sessionProperties = SESSION_PROPERTIES
        )

        val result = SessionCloseProcessorSend("key", inputState, sessionEvent, Instant.now()).execute()
        assertThat(result).isEqualTo(inputState)
    }

    @Test
    fun `If state is CLOSING or CLOSED, no update required`() {
        val sessionEvent = buildSessionEvent(
            MessageDirection.OUTBOUND,
            "sessionId",
            1,
            SessionClose(),
            contextSessionProps = emptyKeyValuePairList()
        )

        val inputState = buildSessionState(
            SessionStateType.CLOSING, 0, mutableListOf(sessionEvent), 0, mutableListOf(),
            sessionProperties = SESSION_PROPERTIES
        )

        val result = SessionCloseProcessorSend("key", inputState, sessionEvent, Instant.now()).execute()
        assertThat(result).isNotNull
        assertThat(result.sendEventsState.undeliveredMessages.size).isEqualTo(0)
        result.sendEventsState.undeliveredMessages.isEmpty()
    }


    @Test
    fun `Send close when status is already CLOSING due to close sent does not modify session state`() {
        val sessionEvent = buildSessionEvent(
            MessageDirection.OUTBOUND,
            "sessionId",
            1,
            SessionClose(),
            contextSessionProps = emptyKeyValuePairList()
        )

        val inputState = buildSessionState(
            SessionStateType.CLOSING, 0, mutableListOf(), 0, mutableListOf(sessionEvent),
            sessionProperties = SESSION_PROPERTIES
        )

        val result = SessionCloseProcessorSend("key", inputState, sessionEvent, Instant.now()).execute()
        assertThat(result).isEqualTo(inputState)
    }
}
