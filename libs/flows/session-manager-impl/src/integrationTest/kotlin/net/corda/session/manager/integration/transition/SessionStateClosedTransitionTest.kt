package net.corda.session.manager.integration.transition

import net.corda.data.flow.event.MessageDirection
import net.corda.data.flow.state.session.SessionState
import net.corda.data.flow.state.session.SessionStateType
import net.corda.session.manager.impl.SessionManagerImpl
import net.corda.test.flow.util.buildSessionState
import net.corda.session.manager.integration.SessionMessageType
import net.corda.session.manager.integration.helper.generateMessage
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.Test
import java.time.Instant

class SessionStateClosedTransitionTest {

    private val sessionManager = SessionManagerImpl()
    private val instant = Instant.now()
    
    @Test
    fun `Send session init when in state closed`() {
        val sessionState = buildClosedState()
        
        val sessionEvent = generateMessage(SessionMessageType.INIT, instant)
        val outputState = sessionManager.processMessageToSend(sessionState, sessionState, sessionEvent, instant)
        Assertions.assertThat(outputState.status).isEqualTo(SessionStateType.ERROR)
    }

    @Test
    fun `Send data when in state closed`() {
        val sessionState = buildClosedState()

        val sessionEvent = generateMessage(SessionMessageType.DATA, instant)
        val outputState = sessionManager.processMessageToSend(sessionState, sessionState, sessionEvent, instant)
        Assertions.assertThat(outputState.status).isEqualTo(SessionStateType.ERROR)
    }

    @Test
    fun `Send close when in state closed`() {
        val sessionState = buildClosedState()

        val sessionEvent = generateMessage(SessionMessageType.CLOSE, instant)
        val outputState = sessionManager.processMessageToSend(sessionState, sessionState, sessionEvent, instant)
        Assertions.assertThat(outputState.status).isEqualTo(SessionStateType.CLOSED)
    }

    @Test
    fun `Receive duplicate init when in state closed`() {
        val sessionState = buildClosedState()

        val sessionEvent = generateMessage(SessionMessageType.INIT, instant, MessageDirection.INBOUND)
        sessionEvent.sequenceNum = 1
        val outputState = sessionManager.processMessageReceived(sessionState, sessionState, sessionEvent, instant)
        Assertions.assertThat(outputState.status).isEqualTo(SessionStateType.CLOSED)
    }

    @Test
    fun `Receive new data when in state closed`() {
        val sessionState = buildClosedState()

        val sessionEvent = generateMessage(SessionMessageType.DATA, instant, MessageDirection.INBOUND)
        sessionEvent.sequenceNum = 2
        val outputState = sessionManager.processMessageReceived(sessionState, sessionState, sessionEvent, instant)
        Assertions.assertThat(outputState.status).isEqualTo(SessionStateType.ERROR)
    }

    @Test
    fun `Receive old data when in state closed`() {
        val sessionState = buildClosedState()

        val sessionEvent = generateMessage(SessionMessageType.DATA, instant, MessageDirection.INBOUND)
        sessionEvent.sequenceNum = 1
        val outputState = sessionManager.processMessageReceived(sessionState, sessionState, sessionEvent, instant)
        Assertions.assertThat(outputState.status).isEqualTo(SessionStateType.CLOSED)
    }

    @Test
    fun `Receive duplicate close when in state closed`() {
        val sessionState = buildClosedState()

        val sessionEvent = generateMessage(SessionMessageType.CLOSE, instant, MessageDirection.INBOUND)
        sessionEvent.sequenceNum = 1
        val outputState = sessionManager.processMessageReceived(sessionState, sessionState, sessionEvent, instant)
        Assertions.assertThat(outputState.status).isEqualTo(SessionStateType.CLOSED)
    }

    @Test
    fun `Receive ack for close when in state closed`() {
        val sessionState = buildClosedState()

        val sessionEvent = generateMessage(SessionMessageType.ACK, instant, MessageDirection.INBOUND)
        sessionEvent.receivedSequenceNum = 2

        val outputState = sessionManager.processMessageReceived(sessionState, sessionState, sessionEvent, instant)
        Assertions.assertThat(outputState.status).isEqualTo(SessionStateType.CLOSED)
    }


    private fun buildClosedState(): SessionState {
        return buildSessionState(
            SessionStateType.CLOSED,
            1,
            listOf(),
            2,
            listOf()
        )
    }
}