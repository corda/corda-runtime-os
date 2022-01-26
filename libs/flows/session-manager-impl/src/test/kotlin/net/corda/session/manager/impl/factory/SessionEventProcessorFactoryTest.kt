package net.corda.session.manager.impl.factory

import net.corda.data.ExceptionEnvelope
import net.corda.data.flow.event.MessageDirection
import net.corda.data.flow.event.SessionEvent
import net.corda.data.flow.event.session.SessionAck
import net.corda.data.flow.event.session.SessionClose
import net.corda.data.flow.event.session.SessionData
import net.corda.data.flow.event.session.SessionError
import net.corda.data.flow.event.session.SessionInit
import net.corda.session.manager.impl.processor.SessionAckProcessorReceived
import net.corda.session.manager.impl.processor.SessionCloseProcessorReceive
import net.corda.session.manager.impl.processor.SessionCloseProcessorSend
import net.corda.session.manager.impl.processor.SessionDataProcessorReceive
import net.corda.session.manager.impl.processor.SessionDataProcessorSend
import net.corda.session.manager.impl.processor.SessionErrorProcessorReceive
import net.corda.session.manager.impl.processor.SessionErrorProcessorSend
import net.corda.session.manager.impl.processor.SessionInitProcessorReceive
import net.corda.session.manager.impl.processor.SessionInitProcessorSend
import net.corda.v5.base.exceptions.CordaRuntimeException
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.Instant

class SessionEventProcessorFactoryTest {

    private val sessionEventProcessorFactory = SessionEventProcessorFactory()

    @Test
    fun testCreateEventReceivedProcessorOutboundMessage() {
        assertThrows<CordaRuntimeException> {
            sessionEventProcessorFactory.createEventReceivedProcessor(
                "key", SessionEvent(MessageDirection.OUTBOUND, 1, "1", 1, null),
                null, Instant.now()
            )
        }
    }

    @Test
    fun testCreateEventToSendProcessorInboundMessage() {
        assertThrows<CordaRuntimeException> {
            sessionEventProcessorFactory.createEventToSendProcessor(
                "key", SessionEvent(MessageDirection.INBOUND, 1, "1", null, null), null,
                Instant.now()
            )
        }
    }

    @Test
    fun testOutboundDataMessage() {
        val processor = sessionEventProcessorFactory.createEventToSendProcessor(
            "key", SessionEvent(
                MessageDirection.OUTBOUND, 1, "1", null,
                SessionData()
            ), null, Instant.now()
        )

        assertThat(processor::class.java).isEqualTo(SessionDataProcessorSend::class.java)
    }

    @Test
    fun testInboundDataMessage() {
        val processor = sessionEventProcessorFactory.createEventReceivedProcessor(
            "key", SessionEvent(
                MessageDirection.INBOUND, 1, "1", 2,
                SessionData()
            ), null, Instant.now()
        )

        assertThat(processor::class.java).isEqualTo(SessionDataProcessorReceive::class.java)
    }

    @Test
    fun testInboundErrorMessage() {
        val processor = sessionEventProcessorFactory.createEventReceivedProcessor(
            "key", SessionEvent(
                MessageDirection.INBOUND, 1, "1", 2,
                SessionError(ExceptionEnvelope())
            ), null, Instant.now()
        )

        assertThat(processor::class.java).isEqualTo(SessionErrorProcessorReceive::class.java)
    }

    @Test
    fun testOutboundErrorMessage() {
        val processor = sessionEventProcessorFactory.createEventToSendProcessor(
            "key", SessionEvent(
                MessageDirection.OUTBOUND, 1, "1", 2,
                SessionError(ExceptionEnvelope())
            ), null, Instant.now()
        )

        assertThat(processor::class.java).isEqualTo(SessionErrorProcessorSend::class.java)
    }

    @Test
    fun testInboundInitMessage() {
        val processor = sessionEventProcessorFactory.createEventReceivedProcessor(
            "key", SessionEvent(
                MessageDirection.INBOUND, 1, "1", 2,
                SessionInit()
            ), null, Instant.now()
        )

        assertThat(processor::class.java).isEqualTo(SessionInitProcessorReceive::class.java)
    }

    @Test
    fun testOutboundInitMessage() {
        val processor = sessionEventProcessorFactory.createEventToSendProcessor(
            "key", SessionEvent(
                MessageDirection.OUTBOUND, 1, "1", 2,
                SessionInit()
            ), null, Instant.now()
        )

        assertThat(processor::class.java).isEqualTo(SessionInitProcessorSend::class.java)
    }

    @Test
    fun testInboundAckMessage() {
        val processor = sessionEventProcessorFactory.createEventReceivedProcessor(
            "key", SessionEvent(
                MessageDirection.INBOUND, 1, "1", 2,
                SessionAck()
            ), null, Instant.now()
        )

        assertThat(processor::class.java).isEqualTo(SessionAckProcessorReceived::class.java)
    }

    @Test
    fun testOutboundAckMessage() {
        assertThrows<NotImplementedError> {
            sessionEventProcessorFactory.createEventToSendProcessor(
                "key", SessionEvent(
                    MessageDirection.OUTBOUND, 1, "1", 1,
                    SessionAck()
                ), null, Instant.now()
            )
        }
    }

    @Test
    fun testInboundCloseMessage() {
        val processor = sessionEventProcessorFactory.createEventReceivedProcessor(
            "key", SessionEvent(
                MessageDirection.INBOUND, 1, "1", 2,
                SessionClose()
            ), null, Instant.now()
        )

        assertThat(processor::class.java).isEqualTo(SessionCloseProcessorReceive::class.java)
    }

    @Test
    fun testOutboundCloseMessage() {
        val processor = sessionEventProcessorFactory.createEventToSendProcessor(
            "key", SessionEvent(
                MessageDirection.OUTBOUND, 1, "1", 2,
                SessionClose()
            ), null, Instant.now()
        )

        assertThat(processor::class.java).isEqualTo(SessionCloseProcessorSend::class.java)
    }

}