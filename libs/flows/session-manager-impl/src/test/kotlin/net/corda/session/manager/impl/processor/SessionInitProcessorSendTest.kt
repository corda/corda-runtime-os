package net.corda.session.manager.impl.processor

import java.time.Instant
import net.corda.data.flow.event.MessageDirection
import net.corda.data.flow.event.session.SessionInit
import net.corda.data.flow.state.session.SessionState
import net.corda.data.flow.state.session.SessionStateType
import net.corda.flow.utils.emptyKeyValuePairList
import net.corda.test.flow.util.buildSessionEvent
import net.corda.test.flow.util.buildSessionState
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
@Disabled //todo CORE-15757
class SessionInitProcessorSendTest {

    private fun createSessionInit() =
        SessionInit("flow", "flowId1", emptyKeyValuePairList(),  emptyKeyValuePairList())

    @Test
    fun `Send init when state is not null`() {
        val sessionInit = buildSessionEvent(
            MessageDirection.OUTBOUND,
            "sessionId",
            1,
            createSessionInit(),
            contextSessionProps = emptyKeyValuePairList()
        )

        val sessionState = buildSessionState(SessionStateType.CREATED, 0, listOf(), 1, listOf(sessionInit))
        val sessionInitProcessor = SessionInitProcessorSend(sessionState, sessionInit, Instant.now())

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
            createSessionInit(),
            contextSessionProps = emptyKeyValuePairList()
        )
        val sessionInitProcessor = SessionInitProcessorSend(SessionState(), sessionInitEvent, Instant.now())

        val sessionState = sessionInitProcessor.execute()

        assertThat(sessionState).isNotNull
        assertThat(sessionState.status).isEqualTo(SessionStateType.CREATED)

        val sendEvents = sessionState.sendEventsState
        assertThat(sendEvents.undeliveredMessages.size).isEqualTo(1)
        assertThat(sendEvents.undeliveredMessages.first()).isEqualTo(sessionInitEvent)
    }
}
