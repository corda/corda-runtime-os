package net.corda.session.manager.impl.processor

import net.corda.data.flow.FlowKey
import net.corda.data.flow.event.MessageDirection
import net.corda.data.flow.event.SessionEvent
import net.corda.data.flow.event.session.SessionInit
import net.corda.data.flow.state.session.SessionStateType
import net.corda.data.identity.HoldingIdentity
import net.corda.session.manager.impl.buildSessionState
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.Instant

class SessionInitProcessorSendTest {

    @Test
    fun `Send init when state is not null`() {
        val initiatingIdentity = HoldingIdentity("ALice", "group1")
        val initiatedIdentity = HoldingIdentity("Bob", "group1")
        val sessionInit = SessionEvent(MessageDirection.OUTBOUND, Instant.now(), "sessionId",1, SessionInit("flow", "cpiId", FlowKey(),
            initiatedIdentity, initiatingIdentity, null))

        val sessionState = buildSessionState(SessionStateType.CREATED, 0, listOf(), 1, listOf(sessionInit))
        val sessionInitProcessor = SessionInitProcessorSend("key", sessionState, sessionInit, Instant.now())

        val updatedState = sessionInitProcessor.execute()

        assertThat(updatedState).isNotNull
        assertThat(updatedState.status).isEqualTo(SessionStateType.ERROR)
    }

    @Test
    fun `Send session Init`() {
        val initiatingIdentity = HoldingIdentity("ALice", "group1")
        val initiatedIdentity = HoldingIdentity("Bob", "group1")
        val sessionInitEvent = SessionEvent(MessageDirection.OUTBOUND, Instant.now(),
            "sessionId",1, SessionInit("flow", "cpiId", FlowKey(), initiatedIdentity, initiatingIdentity, null))
        val sessionInitProcessor = SessionInitProcessorSend("key", null, sessionInitEvent, Instant.now())

        val sessionState = sessionInitProcessor.execute()

        assertThat(sessionState).isNotNull
        assertThat(sessionState.isInitiator).isEqualTo(true)
        assertThat(sessionState.status).isEqualTo(SessionStateType.CREATED)

        val sendEvents = sessionState.sendEventsState
        assertThat(sendEvents.undeliveredMessages.size).isEqualTo(1)
        assertThat(sendEvents.undeliveredMessages.first()).isEqualTo(sessionInitEvent)
    }
}
