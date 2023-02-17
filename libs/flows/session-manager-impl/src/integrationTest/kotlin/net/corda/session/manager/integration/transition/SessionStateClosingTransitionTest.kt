package net.corda.session.manager.integration.transition

import java.time.Instant
import net.corda.data.flow.event.MessageDirection
import net.corda.data.flow.event.SessionEvent
import net.corda.data.flow.state.session.SessionState
import net.corda.data.flow.state.session.SessionStateType
import net.corda.messaging.api.chunking.MessagingChunkFactory
import net.corda.session.manager.impl.SessionManagerImpl
import net.corda.session.manager.impl.factory.SessionEventProcessorFactory
import net.corda.session.manager.integration.SessionMessageType
import net.corda.session.manager.integration.helper.generateMessage
import net.corda.test.flow.util.buildSessionState
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class SessionStateClosingTransitionTest {

    private val messagingChunkFactory : MessagingChunkFactory = mock<MessagingChunkFactory>().apply {
        whenever(createChunkSerializerService(any())).thenReturn(mock())
    }
    private val sessionManager = SessionManagerImpl(SessionEventProcessorFactory(messagingChunkFactory), messagingChunkFactory)

    private val instant = Instant.now()
    private val maxMsgSize = 10000000L

    @Test
    fun `Send duplicate session init when in state closing`() {
        val sessionState = buildClosingState(true)

        val sessionEvent = generateMessage(SessionMessageType.INIT, instant)
        val outputState = sessionManager.processMessageToSend(sessionState, sessionState, sessionEvent, instant, maxMsgSize)
        Assertions.assertThat(outputState.status).isEqualTo(SessionStateType.ERROR)
    }

    @Test
    fun `Send data when initiated close`() {
        val sessionState = buildClosingState(true)

        val sessionEvent = generateMessage(SessionMessageType.DATA, instant)
        val outputState = sessionManager.processMessageToSend(sessionState, sessionState, sessionEvent, instant, maxMsgSize)
        Assertions.assertThat(outputState.status).isEqualTo(SessionStateType.ERROR)
    }

    @Test
    fun `Send data when have received a close`() {
        val sessionState = buildClosingState(false)

        val sessionEvent = generateMessage(SessionMessageType.DATA, instant)
        val outputState = sessionManager.processMessageToSend(sessionState, sessionState, sessionEvent, instant, maxMsgSize)
        Assertions.assertThat(outputState.status).isEqualTo(SessionStateType.ERROR)
    }

    @Test
    fun `Send close when received close`() {
        val sessionState = buildClosingState(false)

        val sessionEvent = generateMessage(SessionMessageType.CLOSE, instant)
        val outputState = sessionManager.processMessageToSend(sessionState, sessionState, sessionEvent, instant, maxMsgSize)
        Assertions.assertThat(outputState.status).isEqualTo(SessionStateType.WAIT_FOR_FINAL_ACK)
    }

    @Test
    fun `Send close when initiated close`() {
        val sessionState = buildClosingState(true)

        val sessionEvent = generateMessage(SessionMessageType.CLOSE, instant)
        val outputState = sessionManager.processMessageToSend(sessionState, sessionState, sessionEvent, instant, maxMsgSize)
        Assertions.assertThat(outputState.status).isEqualTo(SessionStateType.CLOSING)
    }

    @Test
    fun `Receive duplicate init wnhen in state closing`() {
        val sessionState = buildClosingState(true)

        val sessionEvent = generateMessage(SessionMessageType.INIT, instant, MessageDirection.INBOUND)
        sessionEvent.sequenceNum = 1
        val outputState = sessionManager.processMessageReceived(sessionState, sessionState, sessionEvent, instant)
        Assertions.assertThat(outputState.status).isEqualTo(SessionStateType.CLOSING)
    }

    @Test
    fun `Receive new data when already received close`() {
        val sessionState = buildClosingState(false)

        val sessionEvent = generateMessage(SessionMessageType.DATA, instant, MessageDirection.INBOUND)
        sessionEvent.sequenceNum = 2
        val outputState = sessionManager.processMessageReceived(sessionState, sessionState, sessionEvent, instant)
        Assertions.assertThat(outputState.status).isEqualTo(SessionStateType.ERROR)
    }

    @Test
    fun `Receive close when already sent close`() {
        val sessionState = buildClosingState(true)

        val sessionEvent = generateMessage(SessionMessageType.CLOSE, instant, MessageDirection.INBOUND)
        sessionEvent.sequenceNum = 1
        val outputState = sessionManager.processMessageReceived(sessionState, sessionState, sessionEvent, instant)
        Assertions.assertThat(outputState.status).isEqualTo(SessionStateType.WAIT_FOR_FINAL_ACK)
    }

    @Test
    fun `Receive ack for close when initiated close`() {
        val sessionState = buildClosingState(true)

        val sessionEvent = generateMessage(SessionMessageType.ACK, instant, MessageDirection.INBOUND)
        sessionEvent.receivedSequenceNum = 2

        val outputState = sessionManager.processMessageReceived(sessionState, sessionState, sessionEvent, instant)
        Assertions.assertThat(outputState.status).isEqualTo(SessionStateType.CLOSING)
    }


    private fun buildClosingState(initiatedClose: Boolean): SessionState {
        val sentSeqNum: Int
        val receivedSeqNum: Int
        val sentEvents = mutableListOf<SessionEvent>()
        val receivedEvents = mutableListOf<SessionEvent>()
        
        if (initiatedClose) {
            val sessionClose = generateMessage(SessionMessageType.CLOSE, instant)
            sessionClose.sequenceNum = 2
            sentEvents.add(sessionClose)
            sentSeqNum = 2
            receivedSeqNum = 0
        } else {
            val sessionClose = generateMessage(SessionMessageType.CLOSE, instant)
            sessionClose.sequenceNum = 1
            sentSeqNum = 1
            receivedSeqNum = 1
            receivedEvents.add(sessionClose)
        }

        return buildSessionState(
            SessionStateType.CLOSING,
            receivedSeqNum,
            receivedEvents,
            sentSeqNum,
            sentEvents
        )
    }
}