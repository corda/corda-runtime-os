package net.corda.session.manager.impl.processor

import net.corda.data.flow.FlowKey
import net.corda.data.flow.event.MessageDirection
import net.corda.data.flow.event.SessionEvent
import net.corda.data.flow.event.session.SessionAck
import net.corda.data.flow.event.session.SessionInit
import net.corda.data.flow.state.session.SessionStateType
import net.corda.data.identity.HoldingIdentity
import net.corda.session.manager.impl.buildSessionState
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.Instant

class SessionInitProcessorReceiveTest {

    @Test
    fun `Receive duplicate init when state is not null`() {
        val initiatingIdentity = HoldingIdentity("ALice", "group1")
        val initiatedIdentity = HoldingIdentity("Bob", "group1")
        val sessionInitEvent = SessionEvent(
            MessageDirection.OUTBOUND, Instant.now(), "sessionId", 1, SessionInit(
                "flow", "cpiId", FlowKey(),initiatedIdentity, initiatingIdentity, null
            )
        )
        val sessionInitProcessor = SessionInitProcessorReceive("key", buildSessionState(SessionStateType.CONFIRMED, 1, emptyList(), 0,
            emptyList()),
            sessionInitEvent,
            Instant.now())

        val sessionState = sessionInitProcessor.execute()

        assertThat(sessionState).isNotNull
        assertThat(sessionState.sendEventsState.undeliveredMessages.size).isEqualTo(1)
        assertThat(sessionState.sendEventsState.undeliveredMessages.first().payload::class.java).isEqualTo(SessionAck::class.java)
    }

    @Test
    fun `Receive init when state is null`() {
        val initiatingIdentity = HoldingIdentity("ALice", "group1")
        val initiatedIdentity = HoldingIdentity("Bob", "group1")
        val sessionInitEvent = SessionEvent(
            MessageDirection.OUTBOUND, Instant.now(), "sessionId", 1, SessionInit(
                "flow", "cpiId", FlowKey(),initiatedIdentity, initiatingIdentity, null
            )
        )
        val sessionInitProcessor = SessionInitProcessorReceive("key", null, sessionInitEvent, Instant.now())

        val sessionState = sessionInitProcessor.execute()

        assertThat(sessionState).isNotNull
        assertThat(sessionState.status).isEqualTo(SessionStateType.CONFIRMED)
        assertThat(sessionState.isInitiator).isEqualTo(false)
        val receivedEvents = sessionState.receivedEventsState
        assertThat(receivedEvents.lastProcessedSequenceNum).isEqualTo(1)
        assertThat(receivedEvents.undeliveredMessages.size).isEqualTo(1)
        assertThat(receivedEvents.undeliveredMessages.first()).isEqualTo(sessionInitEvent)

        val messagesToSend = sessionState.sendEventsState.undeliveredMessages
        assertThat(messagesToSend.size).isEqualTo(1)
        val sessionAck = messagesToSend.first().payload!!
        assertThat(sessionAck::class.java).isEqualTo(SessionAck::class.java)
    }
}