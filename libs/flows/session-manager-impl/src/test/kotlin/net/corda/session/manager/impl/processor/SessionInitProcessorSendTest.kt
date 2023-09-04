package net.corda.session.manager.impl.processor

import net.corda.data.flow.event.MessageDirection
import net.corda.data.flow.event.session.SessionInit
import net.corda.data.flow.state.session.SessionStateType
import net.corda.flow.utils.emptyKeyValuePairList
import net.corda.test.flow.util.buildSessionEvent
import net.corda.test.flow.util.buildSessionState
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.Instant
class SessionInitProcessorSendTest {

    private fun createSessionInit() =
        SessionInit("flow", "flowId1", emptyKeyValuePairList(),  emptyKeyValuePairList())

    @Test
    fun `Send session Init`() {
        val sessionInitEvent = buildSessionEvent(
            MessageDirection.OUTBOUND,
            "sessionId",
            1,
            createSessionInit(),
            contextSessionProps = emptyKeyValuePairList()
        )

        val sessionState = buildSessionState(SessionStateType.CREATED, 0, emptyList(), 0 , emptyList())
        val sessionInitProcessor = SessionInitProcessorSend(sessionState, sessionInitEvent, Instant.now())

        val updatedSessionState = sessionInitProcessor.execute()

        assertThat(updatedSessionState).isNotNull
        assertThat(updatedSessionState.status).isEqualTo(SessionStateType.CREATED)

        val sendEvents = updatedSessionState.sendEventsState
        assertThat(sendEvents.undeliveredMessages.size).isEqualTo(1)
        assertThat(sendEvents.undeliveredMessages.first()).isEqualTo(sessionInitEvent)
    }
}
