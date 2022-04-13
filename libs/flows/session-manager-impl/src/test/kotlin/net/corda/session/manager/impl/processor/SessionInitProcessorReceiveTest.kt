package net.corda.session.manager.impl.processor

import net.corda.data.flow.event.MessageDirection
import net.corda.data.flow.event.session.SessionError
import net.corda.data.flow.event.session.SessionInit
import net.corda.data.flow.state.session.SessionStateType
import net.corda.data.identity.HoldingIdentity
import net.corda.test.flow.util.buildSessionEvent
import net.corda.test.flow.util.buildSessionState
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.Instant

class SessionInitProcessorReceiveTest {

    @Test
    fun `Receive duplicate init when state is not null`() {
        val sessionInitEvent = buildSessionEvent(
            MessageDirection.INBOUND,
            "sessionId",
            1,
            SessionInit("flow", "cpiId", "flowId1", null)
        )

        val sessionInitProcessor = SessionInitProcessorReceive(
            "key", buildSessionState(
                SessionStateType.CONFIRMED, 1, emptyList(), 0,
                emptyList()
            ),
            sessionInitEvent,
            Instant.now()
        )

        val sessionState = sessionInitProcessor.execute()

        assertThat(sessionState).isNotNull
        assertThat(sessionState.sendEventsState.undeliveredMessages).isEmpty()
        assertThat(sessionState.sendAck).isTrue
    }

    @Test
    fun `Receive init in reply to an init`() {
        val sessionInitEvent = buildSessionEvent(
            MessageDirection.INBOUND,
            "sessionId",
            1,
            SessionInit("flow", "cpiId", "flowId1", null)
        )

        val sessionInitProcessor = SessionInitProcessorReceive(
            "key", buildSessionState(
                SessionStateType.CREATED,
                1,
                emptyList(),
                0,
                listOf(sessionInitEvent)
            ),
            sessionInitEvent,
            Instant.now()
        )

        val sessionState = sessionInitProcessor.execute()

        assertThat(sessionState.status).isEqualTo(SessionStateType.ERROR)
        assertThat(sessionState.sendEventsState.undeliveredMessages.size).isEqualTo(2)
        assertThat(sessionState.sendEventsState.undeliveredMessages.last().payload::class.java).isEqualTo(SessionError::class.java)
    }

    @Test
    fun `Receive init when state is null`() {
        val sessionInitEvent = buildSessionEvent(
            MessageDirection.INBOUND,
            "sessionId",
            1,
            SessionInit("flow", "cpiId", "flowId1", null)
        )

        val sessionInitProcessor = SessionInitProcessorReceive("key", null, sessionInitEvent, Instant.now())

        val sessionState = sessionInitProcessor.execute()

        assertThat(sessionState).isNotNull
        assertThat(sessionState.status).isEqualTo(SessionStateType.CONFIRMED)
        val receivedEvents = sessionState.receivedEventsState
        assertThat(receivedEvents.lastProcessedSequenceNum).isEqualTo(1)
        assertThat(receivedEvents.undeliveredMessages.size).isEqualTo(1)
        assertThat(receivedEvents.undeliveredMessages.first()).isEqualTo(sessionInitEvent)

        assertThat(sessionState.sendEventsState.undeliveredMessages).isEmpty()
        assertThat(sessionState.sendAck).isTrue
    }
}
