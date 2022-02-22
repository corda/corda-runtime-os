package net.corda.session.manager.integration.transition

import net.corda.data.flow.event.MessageDirection
import net.corda.data.flow.event.session.SessionAck
import net.corda.data.flow.state.session.SessionState
import net.corda.data.flow.state.session.SessionStateType
import net.corda.session.manager.impl.SessionManagerImpl
import net.corda.session.manager.impl.buildSessionState
import net.corda.session.manager.integration.SessionMessageType
import net.corda.session.manager.integration.helper.generateMessage
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.Test
import java.time.Instant

class SessionStateConfirmedTransitionTest {

    private val sessionManager = SessionManagerImpl()
    private val instant = Instant.now()

    //TODO - this should be an error
    @Test
    fun `Send session init when in state confirmed`() {
        val sessionState = buildConfirmedState()

        val sessionEvent = generateMessage(SessionMessageType.INIT, instant)
        val outputState = sessionManager.processMessageToSend(sessionState, sessionState, sessionEvent, instant)
        Assertions.assertThat(outputState.status).isEqualTo(SessionStateType.CONFIRMED)
    }

    @Test
    fun `Send data when in state confirmed`() {
        val sessionState = buildConfirmedState()

        val sessionEvent = generateMessage(SessionMessageType.DATA, instant)
        val outputState = sessionManager.processMessageToSend(sessionState, sessionState, sessionEvent, instant)
        Assertions.assertThat(outputState.status).isEqualTo(SessionStateType.CONFIRMED)
    }


    @Test
    fun `Send close when in state confirmed`() {
        val sessionState = buildConfirmedState()

        val sessionEvent = generateMessage(SessionMessageType.CLOSE, instant)
        val outputState = sessionManager.processMessageToSend(sessionState, sessionState, sessionEvent, instant)
        Assertions.assertThat(outputState.status).isEqualTo(SessionStateType.CLOSING)
    }

    @Test
    fun `Receive init when in state confirmed`() {
        val sessionState = buildConfirmedState()

        val sessionEvent = generateMessage(SessionMessageType.INIT, instant, MessageDirection.INBOUND)
        sessionEvent.sequenceNum = 1
        val outputState = sessionManager.processMessageReceived(sessionState, sessionState, sessionEvent, instant)
        Assertions.assertThat(outputState.status).isEqualTo(SessionStateType.CONFIRMED)
    }

    @Test
    fun `Receive data when in state confirmed`() {
        val sessionState = buildConfirmedState()

        val sessionEvent = generateMessage(SessionMessageType.DATA, instant, MessageDirection.INBOUND)
        sessionEvent.sequenceNum = 1
        val outputState = sessionManager.processMessageReceived(sessionState, sessionState, sessionEvent, instant)
        Assertions.assertThat(outputState.status).isEqualTo(SessionStateType.CONFIRMED)
    }

    @Test
    fun `Receive close when in state confirmed`() {
        val sessionState = buildConfirmedState()

        val sessionEvent = generateMessage(SessionMessageType.CLOSE, instant, MessageDirection.INBOUND)
        sessionEvent.sequenceNum = 1
        val outputState = sessionManager.processMessageReceived(sessionState, sessionState, sessionEvent, instant)
        Assertions.assertThat(outputState.status).isEqualTo(SessionStateType.CLOSING)
    }

    @Test
    fun `Receive ack when in state confirmed`() {
        val sessionState = buildConfirmedState()
        val sessionEvent = generateMessage(SessionMessageType.ACK, instant, MessageDirection.INBOUND)
        (sessionEvent.payload as SessionAck).sequenceNum = 1

        val outputState = sessionManager.processMessageReceived(sessionState, sessionState, sessionEvent, instant)
        Assertions.assertThat(outputState.status).isEqualTo(SessionStateType.CONFIRMED)
    }

    private fun buildConfirmedState(): SessionState {
        return buildSessionState(
            SessionStateType.CONFIRMED,
            0,
            listOf(),
            1,
            listOf()
        )
    }
}