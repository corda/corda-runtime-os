package net.corda.session.manager.impl.processor

import java.time.Instant
import net.corda.data.flow.event.MessageDirection
import net.corda.data.flow.event.session.SessionInit
import net.corda.data.flow.state.session.SessionStateType
import net.corda.flow.utils.emptyKeyValuePairList
import net.corda.test.flow.util.buildSessionEvent
import net.corda.test.flow.util.buildSessionState
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class SessionInitProcessorSendTest {

    private fun createSessionInit() =
        SessionInit("flow", "flowId1", emptyKeyValuePairList(), emptyKeyValuePairList(), emptyKeyValuePairList(), null)

    @Test
    fun `Send init when state is not null`() {
        val sessionInit = buildSessionEvent(
            MessageDirection.OUTBOUND,
            "sessionId",
            1,
            createSessionInit()
        )

        val sessionState = buildSessionState(SessionStateType.CREATED, 0, listOf(), 1, listOf(sessionInit))
        val sessionInitProcessor = SessionInitProcessorSend("key", sessionState, sessionInit, Instant.now())

        val updatedState = sessionInitProcessor.execute()

        assertThat(updatedState).isNotNull
        assertThat(updatedState.status).isEqualTo(SessionStateType.ERROR)
    }

    @Test
    fun `Send session Init`() {
        val sessionInitEvent = buildSessionEvent(
            MessageDirection.OUTBOUND,
            "sessionId",
            1,
            createSessionInit()
        )
        val sessionInitProcessor = SessionInitProcessorSend("key", null, sessionInitEvent, Instant.now())

        val sessionState = sessionInitProcessor.execute()

        assertThat(sessionState).isNotNull
        assertThat(sessionState.status).isEqualTo(SessionStateType.CREATED)

        val sendEvents = sessionState.sendEventsState
        assertThat(sendEvents.undeliveredMessages.size).isEqualTo(1)
        assertThat(sendEvents.undeliveredMessages.first()).isEqualTo(sessionInitEvent)
    }
}
