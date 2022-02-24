package net.corda.session.manager.impl.processor

import net.corda.data.ExceptionEnvelope
import net.corda.data.flow.event.MessageDirection
import net.corda.data.flow.event.SessionEvent
import net.corda.data.flow.event.session.SessionError
import net.corda.data.flow.state.session.SessionStateType
import net.corda.session.manager.impl.buildSessionState
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.Test
import java.time.Instant

class SessionErrorProcessorReceiveTest {

    @Test
    fun testNullState() {
        val sessionEvent = SessionEvent(MessageDirection.INBOUND, Instant.now(), "1", 1, SessionError(ExceptionEnvelope()))
        val sessionState = SessionErrorProcessorReceive("Key", null, sessionEvent, ExceptionEnvelope(), Instant.now()).execute()
        Assertions.assertThat(sessionState).isNotNull
        Assertions.assertThat(sessionState.sendEventsState.undeliveredMessages.first().payload::class.java)
            .isEqualTo(SessionError::class.java)
    }

    @Test
    fun testErrorMessage() {
        val sessionEvent = SessionEvent(MessageDirection.INBOUND, Instant.now(), "1", 1, SessionError(ExceptionEnvelope()))
        val inputState = buildSessionState(
            SessionStateType.CONFIRMED, 0, mutableListOf(), 0, mutableListOf()
        )

        val sessionState = SessionErrorProcessorReceive("Key", inputState, sessionEvent, ExceptionEnvelope(), Instant.now())
            .execute()
        Assertions.assertThat(sessionState).isNotNull
        Assertions.assertThat(sessionState.status).isEqualTo(SessionStateType.ERROR)
    }
}
