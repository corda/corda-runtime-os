package net.corda.session.manager.integration.transition

import net.corda.data.flow.event.MessageDirection
import net.corda.data.flow.state.session.SessionState
import net.corda.data.flow.state.session.SessionStateType
import net.corda.session.manager.impl.SessionManagerImpl
import net.corda.session.manager.impl.buildSessionState
import net.corda.session.manager.integration.SessionMessageType
import net.corda.session.manager.integration.helper.generateMessage
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.Test
import java.time.Instant

class SessionStateCreatedTransitionTest {

    private val sessionManager = SessionManagerImpl()
    private val instant = Instant.now()

    @Test
    fun `Send session init when in state created`() {
        val sessionState = buildCreatedState()

        val sessionEvent = generateMessage(SessionMessageType.INIT, instant)
        val outputState = sessionManager.processMessageToSend(sessionState, sessionState, sessionEvent, instant)
        Assertions.assertThat(outputState.status).isEqualTo(SessionStateType.ERROR)
    }

    @Test
    fun `Send data when in state created`() {
        val sessionState = buildCreatedState()

        val sessionEvent = generateMessage(SessionMessageType.DATA, instant)
        val outputState = sessionManager.processMessageToSend(sessionState, sessionState, sessionEvent, instant)
        Assertions.assertThat(outputState.status).isEqualTo(SessionStateType.ERROR)
    }

    @Test
    fun `Send close when in state created`() {
        val sessionState = buildCreatedState()

        val sessionEvent = generateMessage(SessionMessageType.CLOSE, instant)
        val outputState = sessionManager.processMessageToSend(sessionState, sessionState, sessionEvent, instant)
        Assertions.assertThat(outputState.status).isEqualTo(SessionStateType.ERROR)
    }

    @Test
    fun `Session Initiatitor receives init back`() {
        val sessionState = buildCreatedState()

        val sessionEvent = generateMessage(SessionMessageType.INIT, instant, MessageDirection.INBOUND)
        sessionEvent.sequenceNum = 1
        val outputState = sessionManager.processMessageReceived(sessionState, sessionState, sessionEvent, instant)
        Assertions.assertThat(outputState.status).isEqualTo(SessionStateType.ERROR)
    }

    @Test
    fun `Session Initiatitor receives data back`() {
        val sessionState = buildCreatedState()

        val sessionEvent = generateMessage(SessionMessageType.DATA, instant, MessageDirection.INBOUND)
        sessionEvent.sequenceNum = 1
        val outputState = sessionManager.processMessageReceived(sessionState, sessionState, sessionEvent, instant)
        Assertions.assertThat(outputState.status).isEqualTo(SessionStateType.CREATED)
    }

    @Test
    fun `Session Initiator receives close back`() {
        val sessionState = buildCreatedState()

        val sessionEvent = generateMessage(SessionMessageType.CLOSE, instant, MessageDirection.INBOUND)
        sessionEvent.sequenceNum = 1
        val outputState = sessionManager.processMessageReceived(sessionState, sessionState, sessionEvent, instant)
        Assertions.assertThat(outputState.status).isEqualTo(SessionStateType.CLOSING)
    }

    @Test
    fun `Session Initiator receives ack back`() {
        val sessionState = buildCreatedState()
        val sessionEvent = generateMessage(SessionMessageType.ACK, instant, MessageDirection.INBOUND)
        sessionEvent.receivedSequenceNum = 1

        val outputState = sessionManager.processMessageReceived(sessionState, sessionState, sessionEvent, instant)
        Assertions.assertThat(outputState.status).isEqualTo(SessionStateType.CONFIRMED)
    }

    private fun buildCreatedState(): SessionState {
        val sentSessionInit = generateMessage(SessionMessageType.INIT, instant)
        sentSessionInit.sequenceNum = 1

        return buildSessionState(
            SessionStateType.CREATED,
            0,
            listOf(),
            1,
            listOf(sentSessionInit)
        )
    }
}