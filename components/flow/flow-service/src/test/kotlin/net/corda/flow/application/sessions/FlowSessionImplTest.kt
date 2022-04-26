package net.corda.flow.application.sessions

import net.corda.flow.ALICE_X500_NAME
import net.corda.flow.application.services.MockFlowFiberService
import net.corda.flow.fiber.FlowIORequest
import net.corda.flow.pipeline.sandbox.FlowSandboxContextTypes
import net.corda.v5.application.messaging.unwrap
import net.corda.v5.application.serialization.SerializationService
import net.corda.v5.base.exceptions.CordaRuntimeException
import net.corda.v5.serialization.SerializedBytes
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class FlowSessionImplTest {

    private companion object {
        const val SESSION_ID = "session id"
        const val HI = "hi"
        const val HELLO_THERE = "hello there"

        val received = mapOf(SESSION_ID to HELLO_THERE.toByteArray())
    }

    private val serializationService = mock<SerializationService>().apply {
        whenever(serialize(HELLO_THERE)).thenReturn(SerializedBytes(HELLO_THERE.toByteArray()))
        whenever(serialize(HI)).thenReturn(SerializedBytes(HI.toByteArray()))
        whenever(deserialize(HELLO_THERE.toByteArray(), String::class.java)).thenReturn(HELLO_THERE)
        whenever(deserialize(HI.toByteArray(), String::class.java)).thenReturn(HI)
    }



    private val mockFlowFiberService = MockFlowFiberService()
    private val flowFiber = mockFlowFiberService.flowFiber.apply {
        whenever(suspend(any<FlowIORequest.SendAndReceive>())).thenReturn(received)
        whenever(suspend(any<FlowIORequest.Receive>())).thenReturn(received)
    }

    @BeforeEach
    fun setup() {
        mockFlowFiberService.sandboxGroupContext.apply {
            whenever(get(FlowSandboxContextTypes.AMQP_P2P_SERIALIZATION_SERVICE, SerializationService::class.java))
                .thenReturn(serializationService)
        }
    }

    @Test
    fun `calling sendAndReceive with an uninitiated session will cause the flow to suspend to initiate the session`() {
        val session = createSession(initiated = false)
        session.sendAndReceive(String::class.java, HI)
        verify(flowFiber).suspend(any<FlowIORequest.InitiateFlow>())
        verify(flowFiber).suspend(any<FlowIORequest.SendAndReceive>())
    }

    @Test
    fun `calling sendAndReceive with an initiated session will not cause the flow to suspend to initiate the session`() {
        val session = createSession(initiated = true)
        session.sendAndReceive(String::class.java, HI)
        verify(flowFiber, never()).suspend(any<FlowIORequest.InitiateFlow>())
        verify(flowFiber).suspend(any<FlowIORequest.SendAndReceive>())
    }

    @Test
    fun `receiving the wrong object type in sendAndReceive throws an exception`() {
        whenever(serializationService.deserialize(eq(HELLO_THERE.toByteArray()), any<Class<*>>())).thenReturn(1)
        val session = createSession(initiated = true)
        assertThrows<CordaRuntimeException> { session.sendAndReceive(String::class.java, HI) }
    }

    @Test
    fun `sendAndReceive returns the result of the flow's suspension`() {
        val session = createSession(initiated = true)
        assertEquals(HELLO_THERE, session.sendAndReceive(String::class.java, HI).unwrap { it })
        verify(flowFiber).suspend(any<FlowIORequest.SendAndReceive>())
    }

    @Test
    fun `calling receive with an uninitiated session will cause the flow to suspend to initiate the session`() {
        val session = createSession(initiated = false)
        session.receive(String::class.java)
        verify(flowFiber).suspend(any<FlowIORequest.InitiateFlow>())
        verify(flowFiber).suspend(any<FlowIORequest.Receive>())
    }

    @Test
    fun `calling receive with an initiated session will not cause the flow to suspend to initiate the session`() {
        val session = createSession(initiated = true)
        session.receive(String::class.java)
        verify(flowFiber, never()).suspend(any<FlowIORequest.InitiateFlow>())
        verify(flowFiber).suspend(any<FlowIORequest.Receive>())
    }

    @Test
    fun `receiving the wrong object type in receive throws an exception`() {
        whenever(serializationService.deserialize(eq(HELLO_THERE.toByteArray()), any<Class<*>>())).thenReturn(1)
        val session = createSession(initiated = true)
        assertThrows<CordaRuntimeException> { session.receive(String::class.java) }
    }

    @Test
    fun `receive returns the result of the flow's suspension`() {
        val session = createSession(initiated = true)
        assertEquals(HELLO_THERE, session.receive(String::class.java).unwrap { it })
        verify(flowFiber).suspend(any<FlowIORequest.Receive>())
    }

    @Test
    fun `calling send with an uninitiated session will cause the flow to suspend to initiate the session`() {
        val session = createSession(initiated = false)
        session.send(HI)
        verify(flowFiber).suspend(any<FlowIORequest.InitiateFlow>())
        verify(flowFiber).suspend(any<FlowIORequest.Send>())
    }

    @Test
    fun `calling send with an initiated session will not cause the flow to suspend to initiate the session`() {
        val session = createSession(initiated = true)
        session.send(HI)
        verify(flowFiber, never()).suspend(any<FlowIORequest.InitiateFlow>())
        verify(flowFiber).suspend(any<FlowIORequest.Send>())
    }

    @Test
    fun `send suspends the fiber to send a message`() {
        val session = createSession(initiated = true)
        session.send(HI)
        verify(flowFiber).suspend(any<FlowIORequest.Send>())
    }

    private fun createSession(initiated: Boolean): FlowSessionImpl {
        return FlowSessionImpl(
            counterparty = ALICE_X500_NAME,
            sourceSessionId = SESSION_ID,
            mockFlowFiberService,
            initiated
        )
    }
}