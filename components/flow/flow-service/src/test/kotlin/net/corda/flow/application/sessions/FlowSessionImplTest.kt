package net.corda.flow.application.sessions

import net.corda.flow.fiber.FlowFiber
import net.corda.flow.fiber.FlowFiberService
import net.corda.flow.fiber.FlowIORequest
import net.corda.v5.application.flows.unwrap
import net.corda.v5.base.exceptions.CordaRuntimeException
import net.corda.v5.base.types.MemberX500Name
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class FlowSessionImplTest {

    private companion object {
        private const val SESSION_ID = "session id"
        private const val HI = "hi"
        private const val HELLO_THERE = "hello there"

        val received = mapOf(SESSION_ID to HELLO_THERE)
    }

    private val flowFiber = mock<FlowFiber<*>>().apply {
        whenever(suspend(any<FlowIORequest.SendAndReceive>())).thenReturn(received)
        whenever(suspend(any<FlowIORequest.Receive>())).thenReturn(received)
    }

    private val flowFiberService = mock<FlowFiberService>().apply {
        whenever(getExecutingFiber()).thenReturn(flowFiber)
    }

    @Test
    fun `calling sendAndReceive with an uninitiated session will cause the flow to suspend to initiate the session`() {
        val session = createSession(FlowSessionImpl.State.UNINITIATED)
        session.sendAndReceive(String::class.java, HI)
        verify(flowFiber).suspend(any<FlowIORequest.InitiateFlow>())
        verify(flowFiber).suspend(any<FlowIORequest.SendAndReceive>())
    }

    @Test
    fun `calling sendAndReceive with an initiated session will not cause the flow to suspend to initiate the session`() {
        val session = createSession(FlowSessionImpl.State.INITIATED)
        session.sendAndReceive(String::class.java, HI)
        verify(flowFiber, never()).suspend(any<FlowIORequest.InitiateFlow>())
        verify(flowFiber).suspend(any<FlowIORequest.SendAndReceive>())
    }

    @Test
    fun `calling sendAndReceive with a closed session will throw an exception`() {
        val session = createSession(FlowSessionImpl.State.CLOSED)
        assertThrows<CordaRuntimeException> { session.sendAndReceive(String::class.java, HI) }
        verify(flowFiber, never()).suspend(any<FlowIORequest.InitiateFlow>())
    }

    @Test
    fun `sendAndReceive returns the result of the flow's suspension`() {
        val session = createSession(FlowSessionImpl.State.INITIATED)
        assertEquals(HELLO_THERE, session.sendAndReceive(String::class.java, HI).unwrap { it })
        verify(flowFiber).suspend(any<FlowIORequest.SendAndReceive>())
    }

    @Test
    fun `calling receive with an uninitiated session will cause the flow to suspend to initiate the session`() {
        val session = createSession(FlowSessionImpl.State.UNINITIATED)
        session.receive(String::class.java)
        verify(flowFiber).suspend(any<FlowIORequest.InitiateFlow>())
        verify(flowFiber).suspend(any<FlowIORequest.Receive>())
    }

    @Test
    fun `calling receive with an initiated session will not cause the flow to suspend to initiate the session`() {
        val session = createSession(FlowSessionImpl.State.INITIATED)
        session.receive(String::class.java)
        verify(flowFiber, never()).suspend(any<FlowIORequest.InitiateFlow>())
        verify(flowFiber).suspend(any<FlowIORequest.Receive>())
    }

    @Test
    fun `calling receive with a closed session will throw an exception`() {
        val session = createSession(FlowSessionImpl.State.CLOSED)
        assertThrows<CordaRuntimeException> { session.receive(String::class.java) }
        verify(flowFiber, never()).suspend(any<FlowIORequest.InitiateFlow>())
    }

    @Test
    fun `receive returns the result of the flow's suspension`() {
        val session = createSession(FlowSessionImpl.State.INITIATED)
        assertEquals(HELLO_THERE, session.receive(String::class.java).unwrap { it })
        verify(flowFiber).suspend(any<FlowIORequest.Receive>())
    }

    @Test
    fun `calling send with an uninitiated session will cause the flow to suspend to initiate the session`() {
        val session = createSession(FlowSessionImpl.State.UNINITIATED)
        session.send(HI)
        verify(flowFiber).suspend(any<FlowIORequest.InitiateFlow>())
        verify(flowFiber).suspend(any<FlowIORequest.Send>())
    }

    @Test
    fun `calling send with an initiated session will not cause the flow to suspend to initiate the session`() {
        val session = createSession(FlowSessionImpl.State.INITIATED)
        session.send(HI)
        verify(flowFiber, never()).suspend(any<FlowIORequest.InitiateFlow>())
        verify(flowFiber).suspend(any<FlowIORequest.Send>())
    }

    @Test
    fun `calling send with a closed session will throw an exception`() {
        val session = createSession(FlowSessionImpl.State.CLOSED)
        assertThrows<CordaRuntimeException> { session.send(HI) }
        verify(flowFiber, never()).suspend(any<FlowIORequest.InitiateFlow>())
    }

    @Test
    fun `send suspends the fiber to send a message`() {
        val session = createSession(FlowSessionImpl.State.INITIATED)
        session.send(HI)
        verify(flowFiber).suspend(any<FlowIORequest.Send>())
    }

    private fun createSession(state: FlowSessionImpl.State): FlowSessionImpl {
        return FlowSessionImpl(
            counterparty = MemberX500Name(
                commonName = "Alice",
                organisation = "Alice Corp",
                locality = "LDN",
                country = "GB"
            ),
            sourceSessionId = SESSION_ID,
            flowFiberService,
            state
        )
    }
}