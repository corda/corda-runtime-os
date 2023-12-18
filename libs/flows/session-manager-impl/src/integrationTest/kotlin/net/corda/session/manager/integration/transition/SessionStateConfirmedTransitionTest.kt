package net.corda.session.manager.integration.transition

import net.corda.data.flow.event.MessageDirection
import net.corda.data.flow.state.session.SessionState
import net.corda.data.flow.state.session.SessionStateType
import net.corda.flow.utils.INITIATED_SESSION_ID_SUFFIX
import net.corda.flow.utils.KeyValueStore
import net.corda.messaging.api.chunking.MessagingChunkFactory
import net.corda.session.manager.Constants
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

class SessionStateConfirmedTransitionTest {

    private val messagingChunkFactory : MessagingChunkFactory = mock<MessagingChunkFactory>().apply {
        whenever(createChunkSerializerService(any())).thenReturn(mock())
    }
    private val sessionManager = SessionManagerImpl(SessionEventProcessorFactory(messagingChunkFactory), messagingChunkFactory)

    private val instant = Instant.now()
    private val maxMsgSize = 10000000L

    @Test
    fun `Send data when in state confirmed`() {
        val sessionState = buildConfirmedState()

        val sessionEvent = generateMessage(SessionMessageType.DATA, instant)
        val outputState = sessionManager.processMessageToSend(sessionState, sessionState, sessionEvent, instant, maxMsgSize)
        Assertions.assertThat(outputState.status).isEqualTo(SessionStateType.CONFIRMED)
    }

    @Test
    fun `Send close when in state confirmed`() {
        val sessionState = buildConfirmedState()
        sessionState.requireClose(true)
        sessionState.sessionId += INITIATED_SESSION_ID_SUFFIX
        val sessionEvent = generateMessage(SessionMessageType.CLOSE, instant)

        val outputState = sessionManager.processMessageToSend(sessionState, sessionState, sessionEvent, instant, maxMsgSize)
        Assertions.assertThat(outputState.status).isEqualTo(SessionStateType.CLOSED)
    }

    @Test
    fun `Receive init when in state confirmed`() {
        val sessionState = buildConfirmedState()

        val sessionEvent = generateMessage(SessionMessageType.COUNTERPARTY_INFO, instant, MessageDirection.INBOUND)
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

    private fun buildConfirmedState(): SessionState {
        return buildSessionState(
            SessionStateType.CONFIRMED,
            0,
            listOf(),
            1,
            listOf(),
            sessionProperties = KeyValueStore().apply {
                put(Constants.FLOW_SESSION_REQUIRE_CLOSE, false.toString())
            }.avro
        )
    }

    private fun SessionState.requireClose(requireClose: Boolean) =
        KeyValueStore(sessionProperties).apply {
            put(Constants.FLOW_SESSION_REQUIRE_CLOSE, requireClose.toString())
        }
}