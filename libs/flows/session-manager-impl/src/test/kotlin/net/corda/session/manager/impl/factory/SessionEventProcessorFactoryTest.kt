package net.corda.session.manager.impl.factory

import net.corda.data.ExceptionEnvelope
import net.corda.data.flow.event.MessageDirection
import net.corda.data.flow.event.session.SessionClose
import net.corda.data.flow.event.session.SessionCounterpartyInfoRequest
import net.corda.data.flow.event.session.SessionCounterpartyInfoResponse
import net.corda.data.flow.event.session.SessionData
import net.corda.data.flow.event.session.SessionError
import net.corda.data.flow.state.session.SessionState
import net.corda.flow.utils.emptyKeyValuePairList
import net.corda.messaging.api.chunking.MessagingChunkFactory
import net.corda.session.manager.SessionManagerException
import net.corda.session.manager.impl.processor.SessionCloseProcessorReceive
import net.corda.session.manager.impl.processor.SessionCloseProcessorSend
import net.corda.session.manager.impl.processor.SessionCounterpartyInfoRequestProcessorReceive
import net.corda.session.manager.impl.processor.SessionCounterpartyInfoRequestProcessorSend
import net.corda.session.manager.impl.processor.SessionCounterpartyInfoResponseProcessorReceive
import net.corda.session.manager.impl.processor.SessionDataProcessorReceive
import net.corda.session.manager.impl.processor.SessionDataProcessorSend
import net.corda.session.manager.impl.processor.SessionErrorProcessorReceive
import net.corda.session.manager.impl.processor.SessionErrorProcessorSend
import net.corda.test.flow.util.buildSessionEvent
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.time.Instant

class SessionEventProcessorFactoryTest {

    private val messagingChunkFactory: MessagingChunkFactory = mock<MessagingChunkFactory>().apply {
        whenever(createChunkSerializerService(any())).thenReturn(mock())
    }
    private val sessionEventProcessorFactory = SessionEventProcessorFactory(messagingChunkFactory)

    private companion object {
        val maxMsgSize = 1000000L
        val sessionState = SessionState()
    }

    @Test
    fun testCreateEventReceivedProcessorOutboundMessage() {
        assertThrows<SessionManagerException> {
            sessionEventProcessorFactory.createEventReceivedProcessor(
                "key", buildSessionEvent(
                    MessageDirection.OUTBOUND, "sessionId", 1, SessionData(), contextSessionProps = emptyKeyValuePairList()
                ), null, Instant.now()
            )
        }
    }

    @Test
    fun testCreateEventToSendProcessorInboundMessage() {
        assertThrows<SessionManagerException> {
            sessionEventProcessorFactory.createEventToSendProcessor(
                "key",
                buildSessionEvent(MessageDirection.INBOUND, "sessionId", 1, SessionData(), contextSessionProps = emptyKeyValuePairList()),
                sessionState,
                Instant.now(),
                maxMsgSize
            )
        }
    }

    @Test
    fun testOutboundDataMessage() {
        val processor = sessionEventProcessorFactory.createEventToSendProcessor(
            "key",
            buildSessionEvent(MessageDirection.OUTBOUND, "sessionId", 1, SessionData(), contextSessionProps = emptyKeyValuePairList()),
            sessionState,
            Instant.now(),
            maxMsgSize
        )

        verify(messagingChunkFactory, times(1)).createChunkSerializerService(any())
        assertThat(processor::class.java).isEqualTo(SessionDataProcessorSend::class.java)
    }

    @Test
    fun testInboundDataMessage() {
        val processor = sessionEventProcessorFactory.createEventReceivedProcessor(
            "key",
            buildSessionEvent(MessageDirection.INBOUND, "sessionId", 1, SessionData(), contextSessionProps = emptyKeyValuePairList()),
            null,
            Instant.now()
        )

        assertThat(processor::class.java).isEqualTo(SessionDataProcessorReceive::class.java)
    }

    @Test
    fun testInboundErrorMessage() {
        val processor = sessionEventProcessorFactory.createEventReceivedProcessor(
            "key", buildSessionEvent(
                MessageDirection.INBOUND, "sessionId", 1, SessionError(ExceptionEnvelope()), contextSessionProps = emptyKeyValuePairList()
            ), null, Instant.now()
        )

        assertThat(processor::class.java).isEqualTo(SessionErrorProcessorReceive::class.java)
    }

    @Test
    fun testOutboundErrorMessage() {
        val processor = sessionEventProcessorFactory.createEventToSendProcessor(
            "key", buildSessionEvent(
                MessageDirection.OUTBOUND, "sessionId", 1, SessionError(ExceptionEnvelope()), contextSessionProps = emptyKeyValuePairList()
            ), sessionState, Instant.now(), maxMsgSize
        )

        assertThat(processor::class.java).isEqualTo(SessionErrorProcessorSend::class.java)
    }

    @Test
    fun `Receive a SessionCounterpartyInfoRequest`() {
        val processor = sessionEventProcessorFactory.createEventReceivedProcessor(
            "key",
            buildSessionEvent(
                MessageDirection.INBOUND,
                "sessionId",
                1,
                SessionCounterpartyInfoRequest(),
                contextSessionProps = emptyKeyValuePairList()
            ),
            null,
            Instant.now()
        )

        assertThat(processor::class.java).isEqualTo(SessionCounterpartyInfoRequestProcessorReceive::class.java)
    }

    @Test
    fun `Receive a SessionCounterpartyInfoResponse`() {
        val processor = sessionEventProcessorFactory.createEventReceivedProcessor(
            "key",
            buildSessionEvent(
                MessageDirection.INBOUND,
                "sessionId",
                1,
                SessionCounterpartyInfoResponse(),
                contextSessionProps = emptyKeyValuePairList()
            ),
            null,
            Instant.now()
        )

        assertThat(processor::class.java).isEqualTo(SessionCounterpartyInfoResponseProcessorReceive::class.java)
    }

    @Test
    fun `Send a SessionCounterpartyInfoRequest`() {
        val processor = sessionEventProcessorFactory.createEventToSendProcessor(
            "key",
            buildSessionEvent(
                MessageDirection.OUTBOUND,
                "sessionId",
                1,
                SessionCounterpartyInfoRequest(),
                contextSessionProps = emptyKeyValuePairList()
            ),
            sessionState,
            Instant.now(),
            maxMsgSize
        )

        assertThat(processor::class.java).isEqualTo(SessionCounterpartyInfoRequestProcessorSend::class.java)
    }

    @Test
    fun testInboundCloseMessage() {
        val processor = sessionEventProcessorFactory.createEventReceivedProcessor(
            "key",
            buildSessionEvent(MessageDirection.INBOUND, "sessionId", 1, SessionClose(), contextSessionProps = emptyKeyValuePairList()),
            null,
            Instant.now()
        )

        assertThat(processor::class.java).isEqualTo(SessionCloseProcessorReceive::class.java)
    }

    @Test
    fun testOutboundCloseMessage() {
        val processor = sessionEventProcessorFactory.createEventToSendProcessor(
            "key",
            buildSessionEvent(MessageDirection.OUTBOUND, "sessionId", 1, SessionClose(), contextSessionProps = emptyKeyValuePairList()),
            sessionState,
            Instant.now(),
            maxMsgSize
        )

        assertThat(processor::class.java).isEqualTo(SessionCloseProcessorSend::class.java)
    }
}
