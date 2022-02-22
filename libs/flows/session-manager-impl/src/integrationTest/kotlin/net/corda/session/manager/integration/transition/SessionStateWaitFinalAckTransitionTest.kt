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

class SessionStateWaitFinalAckTransitionTest {

    private val sessionManager = SessionManagerImpl()
    private val instant = Instant.now()

    //TODO - this should be an error
    @Test
    fun `Send session init when in state wait for final ack`() {
        val sessionState = buildWaitFinalAck()

        val sessionEvent = generateMessage(SessionMessageType.INIT, instant)
        val outputState = sessionManager.processMessageToSend(sessionState, sessionState, sessionEvent, Instant.now())
        Assertions.assertThat(outputState.status).isEqualTo(SessionStateType.WAIT_FOR_FINAL_ACK)
    }

    @Test
    fun `Send data when in state wait for final ack`() {
        val sessionState = buildWaitFinalAck()

        val sessionEvent = generateMessage(SessionMessageType.DATA, instant)
        val outputState = sessionManager.processMessageToSend(sessionState, sessionState, sessionEvent, Instant.now())
        Assertions.assertThat(outputState.status).isEqualTo(SessionStateType.ERROR)
    }

    @Test
    fun `Send close when in state wait for final ack`() {
        val sessionState = buildWaitFinalAck()

        val sessionEvent = generateMessage(SessionMessageType.CLOSE, instant)
        val outputState = sessionManager.processMessageToSend(sessionState, sessionState, sessionEvent, Instant.now())
        Assertions.assertThat(outputState.status).isEqualTo(SessionStateType.ERROR)
    }

    @Test
    fun `Receive duplicate init when in state wait for final ack`() {
        val sessionState = buildWaitFinalAck()

        val sessionEvent = generateMessage(SessionMessageType.INIT, instant, MessageDirection.INBOUND)
        sessionEvent.sequenceNum = 1
        val outputState = sessionManager.processMessageReceived(sessionState, sessionState, sessionEvent, Instant.now())
        Assertions.assertThat(outputState.status).isEqualTo(SessionStateType.WAIT_FOR_FINAL_ACK)
    }

    @Test
    fun `Receive new data when in state wait for final ack`() {
        val sessionState = buildWaitFinalAck()

        val sessionEvent = generateMessage(SessionMessageType.DATA, instant, MessageDirection.INBOUND)
        sessionEvent.sequenceNum = 2
        val outputState = sessionManager.processMessageReceived(sessionState, sessionState, sessionEvent, Instant.now())
        Assertions.assertThat(outputState.status).isEqualTo(SessionStateType.ERROR)
    }

    @Test
    fun `Receive duplicate close when in state wait for final ack`() {
        val sessionState = buildWaitFinalAck()

        val sessionEvent = generateMessage(SessionMessageType.CLOSE, instant, MessageDirection.INBOUND)
        sessionEvent.sequenceNum = 1
        val outputState = sessionManager.processMessageReceived(sessionState, sessionState, sessionEvent, Instant.now())
        Assertions.assertThat(outputState.status).isEqualTo(SessionStateType.WAIT_FOR_FINAL_ACK)
    }

    @Test
    fun `Receive ack for close when in state wait for final ack`() {
        val sessionState = buildWaitFinalAck()

        val sessionEvent = generateMessage(SessionMessageType.ACK, instant, MessageDirection.INBOUND)
        (sessionEvent.payload as SessionAck).sequenceNum = 2

        val outputState = sessionManager.processMessageReceived(sessionState, sessionState, sessionEvent, Instant.now())
        Assertions.assertThat(outputState.status).isEqualTo(SessionStateType.CLOSED)
    }


    private fun buildWaitFinalAck(): SessionState {
        val sessionCloseSent = generateMessage(SessionMessageType.CLOSE, instant)
        sessionCloseSent.sequenceNum = 2

        val sessionCloseReceived = generateMessage(SessionMessageType.CLOSE, instant)
        sessionCloseReceived.sequenceNum = 1

        return buildSessionState(
            SessionStateType.WAIT_FOR_FINAL_ACK,
            1,
            listOf(sessionCloseReceived),
            2,
            listOf(sessionCloseSent)
        )
    }
}