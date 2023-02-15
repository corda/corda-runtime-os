package net.corda.session.manager.integration.transition

import java.time.Instant
import net.corda.data.flow.event.MessageDirection
import net.corda.data.flow.state.session.SessionState
import net.corda.data.flow.state.session.SessionStateType
import net.corda.session.manager.impl.SessionManagerImpl
import net.corda.session.manager.impl.factory.SessionEventProcessorFactory
import net.corda.session.manager.integration.SessionMessageType
import net.corda.session.manager.integration.helper.generateMessage
import net.corda.test.flow.util.buildSessionState
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock

class SessionStateWaitFinalAckTransitionTest {

    private val sessionManager = SessionManagerImpl(SessionEventProcessorFactory(mock()), mock())

    private val instant = Instant.now()
    private val maxMsgSize = 10000000L

    @Test
    fun `Send session init when in state wait for final ack`() {
        val sessionState = buildWaitFinalAck()

        val sessionEvent = generateMessage(SessionMessageType.INIT, instant)
        val outputState = sessionManager.processMessageToSend(sessionState, sessionState, sessionEvent, Instant.now(), maxMsgSize)
        Assertions.assertThat(outputState.status).isEqualTo(SessionStateType.ERROR)
    }

    @Test
    fun `Send data when in state wait for final ack`() {
        val sessionState = buildWaitFinalAck()

        val sessionEvent = generateMessage(SessionMessageType.DATA, instant)
        val outputState = sessionManager.processMessageToSend(sessionState, sessionState, sessionEvent, Instant.now(), maxMsgSize)
        Assertions.assertThat(outputState.status).isEqualTo(SessionStateType.ERROR)
    }

    @Test
    fun `Send close when in state wait for final ack`() {
        val sessionState = buildWaitFinalAck()

        val sessionEvent = generateMessage(SessionMessageType.CLOSE, instant)
        val outputState = sessionManager.processMessageToSend(sessionState, sessionState, sessionEvent, Instant.now(), maxMsgSize)
        Assertions.assertThat(outputState.status).isEqualTo(SessionStateType.WAIT_FOR_FINAL_ACK)
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
        sessionEvent.receivedSequenceNum = 2

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