package net.corda.session.manager.impl.factory

import java.time.Instant
import net.corda.data.ExceptionEnvelope
import net.corda.data.flow.event.MessageDirection
import net.corda.data.flow.event.session.SessionAck
import net.corda.data.flow.event.session.SessionClose
import net.corda.data.flow.event.session.SessionData
import net.corda.data.flow.event.session.SessionError
import net.corda.data.flow.event.session.SessionInit
import net.corda.session.manager.SessionManagerException
import net.corda.session.manager.impl.processor.SessionAckProcessorReceive
import net.corda.session.manager.impl.processor.SessionCloseProcessorReceive
import net.corda.session.manager.impl.processor.SessionCloseProcessorSend
import net.corda.session.manager.impl.processor.SessionDataProcessorReceive
import net.corda.session.manager.impl.processor.SessionDataProcessorSend
import net.corda.session.manager.impl.processor.SessionErrorProcessorReceive
import net.corda.session.manager.impl.processor.SessionErrorProcessorSend
import net.corda.session.manager.impl.processor.SessionInitProcessorReceive
import net.corda.session.manager.impl.processor.SessionInitProcessorSend
import net.corda.test.flow.util.buildSessionEvent
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.mock

class SessionEventProcessorFactoryTest {

    private val sessionEventProcessorFactory = SessionEventProcessorFactory(mock())

    private companion object {
        val maxMsgSize = 1000000L
    }

    @Test
    fun testCreateEventReceivedProcessorOutboundMessage() {
        assertThrows<SessionManagerException> {
            sessionEventProcessorFactory.createEventReceivedProcessor(
                "key", buildSessionEvent(MessageDirection.OUTBOUND, "sessionId", 1, SessionData()),
                null, Instant.now()
            )
        }
    }

    @Test
    fun testCreateEventToSendProcessorInboundMessage() {
        assertThrows<SessionManagerException> {
            sessionEventProcessorFactory.createEventToSendProcessor(
                "key", buildSessionEvent(MessageDirection.INBOUND, "sessionId", 1, SessionData()), null, Instant.now(), maxMsgSize
            )
        }
    }

    @Test
    fun testOutboundDataMessage() {
        val processor = sessionEventProcessorFactory.createEventToSendProcessor(
            "key", buildSessionEvent(MessageDirection.OUTBOUND, "sessionId", 1, SessionData()), null, Instant.now(), maxMsgSize
        )

        assertThat(processor::class.java).isEqualTo(SessionDataProcessorSend::class.java)
    }

    @Test
    fun testInboundDataMessage() {
        val processor = sessionEventProcessorFactory.createEventReceivedProcessor(
            "key", buildSessionEvent(MessageDirection.INBOUND, "sessionId", 1, SessionData()), null, Instant.now()
        )

        assertThat(processor::class.java).isEqualTo(SessionDataProcessorReceive::class.java)
    }

    @Test
    fun testInboundErrorMessage() {
        val processor = sessionEventProcessorFactory.createEventReceivedProcessor(
            "key", buildSessionEvent(
                MessageDirection.INBOUND, "sessionId", 1, SessionError(ExceptionEnvelope())
            ), null, Instant.now()
        )

        assertThat(processor::class.java).isEqualTo(SessionErrorProcessorReceive::class.java)
    }

    @Test
    fun testOutboundErrorMessage() {
        val processor = sessionEventProcessorFactory.createEventToSendProcessor(
            "key",
            buildSessionEvent(MessageDirection.OUTBOUND, "sessionId", 1, SessionError(ExceptionEnvelope())),
            null,
            Instant.now(),
            maxMsgSize
        )

        assertThat(processor::class.java).isEqualTo(SessionErrorProcessorSend::class.java)
    }

    @Test
    fun testInboundInitMessage() {
        val processor = sessionEventProcessorFactory.createEventReceivedProcessor(
            "key", buildSessionEvent(MessageDirection.INBOUND, "sessionId", 1, SessionInit()), null, Instant.now()
        )

        assertThat(processor::class.java).isEqualTo(SessionInitProcessorReceive::class.java)
    }

    @Test
    fun testOutboundInitMessage() {
        val processor = sessionEventProcessorFactory.createEventToSendProcessor(
            "key", buildSessionEvent(MessageDirection.OUTBOUND, "sessionId", 1, SessionInit()), null, Instant.now(), maxMsgSize
        )

        assertThat(processor::class.java).isEqualTo(SessionInitProcessorSend::class.java)
    }

    @Test
    fun testInboundAckMessage() {
        val processor = sessionEventProcessorFactory.createEventReceivedProcessor(
            "key", buildSessionEvent(MessageDirection.INBOUND, "sessionId", 1, SessionAck()), null, Instant.now()
        )

        assertThat(processor::class.java).isEqualTo(SessionAckProcessorReceive::class.java)
    }

    @Test
    fun testOutboundAckMessage() {
        assertThrows<NotImplementedError> {
            sessionEventProcessorFactory.createEventToSendProcessor(
                "key", buildSessionEvent(MessageDirection.OUTBOUND, "sessionId", 1, SessionAck()), null, Instant.now(), maxMsgSize
            )
        }
    }

    @Test
    fun testInboundCloseMessage() {
        val processor = sessionEventProcessorFactory.createEventReceivedProcessor(
            "key", buildSessionEvent(MessageDirection.INBOUND, "sessionId", 1, SessionClose()), null, Instant.now()
        )

        assertThat(processor::class.java).isEqualTo(SessionCloseProcessorReceive::class.java)
    }

    @Test
    fun testOutboundCloseMessage() {
        val processor = sessionEventProcessorFactory.createEventToSendProcessor(
            "key", buildSessionEvent(MessageDirection.OUTBOUND, "sessionId", 1, SessionClose()), null, Instant.now(), maxMsgSize
        )

        assertThat(processor::class.java).isEqualTo(SessionCloseProcessorSend::class.java)
    }
}
