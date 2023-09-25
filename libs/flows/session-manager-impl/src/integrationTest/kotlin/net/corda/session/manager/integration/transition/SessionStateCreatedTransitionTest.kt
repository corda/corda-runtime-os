package net.corda.session.manager.integration.transition

import net.corda.data.flow.event.MessageDirection
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
import java.time.Instant

class SessionStateCreatedTransitionTest {

    private val messagingChunkFactory : MessagingChunkFactory = mock<MessagingChunkFactory>().apply {
        whenever(createChunkSerializerService(any())).thenReturn(mock())
    }
    private val sessionManager = SessionManagerImpl(SessionEventProcessorFactory(messagingChunkFactory), messagingChunkFactory)

    private val instant = Instant.now()
    private val maxMsgSize = 10000000L

    @Test
    fun `Send counterparty request  when in state created`() {
        val sessionState = buildCreatedState()

        val sessionEvent = generateMessage(SessionMessageType.COUNTERPARTY_INFO, instant)
        val outputState = sessionManager.processMessageToSend(sessionState, sessionState, sessionEvent, instant, maxMsgSize)
        Assertions.assertThat(outputState.status).isEqualTo(SessionStateType.CREATED)
    }

    @Test
    fun `Send data when in state created`() {
        val sessionState = buildCreatedState()

        val sessionEvent = generateMessage(SessionMessageType.DATA, instant)
        val outputState = sessionManager.processMessageToSend(sessionState, sessionState, sessionEvent, instant, maxMsgSize)
        Assertions.assertThat(outputState.status).isEqualTo(SessionStateType.CREATED)
    }

    @Test
    fun `Send close when in state created`() {
        val sessionState = buildCreatedState()

        val sessionEvent = generateMessage(SessionMessageType.CLOSE, instant)
        val outputState = sessionManager.processMessageToSend(sessionState, sessionState, sessionEvent, instant, maxMsgSize)
        Assertions.assertThat(outputState.status).isEqualTo(SessionStateType.ERROR)
    }

    @Test
    fun `Session Initiatitor receives data back`() {
        val sessionState = buildCreatedState()

        val sessionEvent = generateMessage(SessionMessageType.DATA, instant, MessageDirection.INBOUND)
        sessionEvent.sequenceNum = 1
        val outputState = sessionManager.processMessageReceived(sessionState, sessionState, sessionEvent, instant)
        Assertions.assertThat(outputState.status).isEqualTo(SessionStateType.CONFIRMED)
    }

    @Test
    fun `Session Initiator receives close back`() {
        val sessionState = buildCreatedState()

        val sessionEvent = generateMessage(SessionMessageType.CLOSE, instant, MessageDirection.INBOUND)
        sessionEvent.sequenceNum = 1
        val outputState = sessionManager.processMessageReceived(sessionState, sessionState, sessionEvent, instant)
        Assertions.assertThat(outputState.status).isEqualTo(SessionStateType.CLOSING)
    }

    private fun buildCreatedState(): SessionState {
        val sentSessionCOUNTERPARTYINFO = generateMessage(SessionMessageType.COUNTERPARTY_INFO, instant)
        sentSessionCOUNTERPARTYINFO.sequenceNum = 1

        return buildSessionState(
            SessionStateType.CREATED,
            0,
            listOf(),
            1,
            listOf(sentSessionCOUNTERPARTYINFO)
        )
    }
}