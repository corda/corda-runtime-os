package net.corda.testutils.tools

import net.corda.testutils.exceptions.UnexpectedRequestException
import net.corda.testutils.flows.PingAckMessage
import net.corda.v5.application.messaging.FlowSession
import net.corda.v5.application.messaging.UntrustworthyData
import net.corda.v5.application.messaging.receive
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class ResponderMockTest {

    @Test
    fun `should respond to request with mocked responses in turn`() {
        // Given a ResponderMock with some preset responses
        val responderMock = ResponderMock<PingAckMessage, PingAckMessage>()
        responderMock.whenever(PingAckMessage("Ping"), listOf("...","...","Ack").map { PingAckMessage(it) })
        responderMock.whenever(PingAckMessage("Pong"), listOf("Ock").map {PingAckMessage(it)})

        // When we provide a session which lets it receive one of those responses
        val session = mock<FlowSession>()
        whenever(session.receive<Any>()).thenReturn(UntrustworthyData(PingAckMessage("Ping")))
        responderMock.call(session)

        // Then it should respond with the preset list
        verify(session, times(2)).send(PingAckMessage("..."))
        verify(session, times(1)).send(PingAckMessage("Ack"))
    }

    @Test
    fun `should throw an exception if a request is unrecognized`() {
        // Given a ResponderMock with some preset responses
        val responderMock = ResponderMock<PingAckMessage, PingAckMessage>()
        responderMock.whenever(PingAckMessage("Ping"), listOf("...","...","Ack").map { PingAckMessage(it) })
        responderMock.whenever(PingAckMessage("Pong"), listOf("Ock").map {PingAckMessage(it)})

        // When we provide a session which gives a response that has not been registered
        val session = mock<FlowSession>()
        whenever(session.receive<Any>()).thenReturn(UntrustworthyData(PingAckMessage("Pang")))

        // Then it should throw an exception
        assertThrows<UnexpectedRequestException> { responderMock.call(session) }
    }
}