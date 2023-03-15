package net.corda.flow.application.sessions

import net.corda.flow.ALICE_X500_NAME
import net.corda.flow.application.serialization.DeserializedWrongAMQPObjectException
import net.corda.flow.application.serialization.SerializationServiceInternal
import net.corda.flow.application.services.MockFlowFiberService
import net.corda.flow.application.sessions.impl.FlowSessionImpl
import net.corda.flow.fiber.FlowIORequest
import net.corda.flow.state.FlowContext
import net.corda.v5.base.exceptions.CordaRuntimeException
import net.corda.v5.serialization.SerializedBytes
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class FlowSessionImplTest {

    private companion object {
        const val SESSION_ID = "session id"
        const val HI = "hi"
        const val HELLO_THERE = "hello there"

        val received = mapOf(SESSION_ID to HELLO_THERE.toByteArray())
    }

    private val mockFlowFiberService = MockFlowFiberService()
    private val serializationService = mock<SerializationServiceInternal>().apply {
        whenever(serialize(HELLO_THERE)).thenReturn(SerializedBytes(HELLO_THERE.toByteArray()))
        whenever(serialize(HI)).thenReturn(SerializedBytes(HI.toByteArray()))
        whenever(deserializeAndCheckType(HELLO_THERE.toByteArray(), String::class.java)).thenReturn(HELLO_THERE)
        whenever(deserializeAndCheckType(HI.toByteArray(), String::class.java)).thenReturn(HI)
    }

    private val flowFiber = mockFlowFiberService.flowFiber.apply {
        whenever(suspend(any<FlowIORequest.SendAndReceive>())).thenReturn(received)
        whenever(suspend(any<FlowIORequest.Receive>())).thenReturn(received)
    }

    private val userKey = "userKey"
    private val userValue = "userValue"

    private val platformKey = "platformKey"
    private val platformValue = "platformValue"

    private val userContext = mapOf(userKey to userValue)
    private val platformContext = mapOf(platformKey to platformValue)

    private val flowContext = mock<FlowContext>().apply {
        whenever(flattenUserProperties()).thenReturn(userContext)
        whenever(flattenPlatformProperties()).thenReturn(platformContext)

        whenever(get(userKey)).thenReturn(userValue)
        whenever(get(platformKey)).thenReturn(platformValue)
    }

    @Test
    fun `calling sendAndReceive with an initiating session will cause the flow to suspend to initiate the session`() {
        val session = createInitiatingSession()
        session.sendAndReceive(String::class.java, HI)

        val flowIORequestCapture = argumentCaptor<FlowIORequest<*>>()

        verify(flowFiber, times(1)).suspend(flowIORequestCapture.capture())

        assertTrue(flowIORequestCapture.firstValue is FlowIORequest.SendAndReceive)
    }

    @Test
    fun `calling sendAndReceive with an initiated session will not cause the flow to suspend to initiate the session`() {
        val session = createInitiatedSession()
        session.sendAndReceive(String::class.java, HI)
        verify(flowFiber).suspend(any<FlowIORequest.SendAndReceive>())
    }

    @Test
    fun `receiving the wrong object type in sendAndReceive throws an exception`() {
        whenever(serializationService.deserializeAndCheckType(eq(HELLO_THERE.toByteArray()), any<Class<*>>()))
            .thenThrow(DeserializedWrongAMQPObjectException(String::class.java, Int::class.java, 1, "wrong"))

        val session = createInitiatedSession()
        assertThrows<CordaRuntimeException> { session.sendAndReceive(String::class.java, HI) }
    }

    @Test
    fun `sendAndReceive returns the result of the flow's suspension`() {
        val session = createInitiatedSession()
        assertEquals(HELLO_THERE, session.sendAndReceive(String::class.java, HI))
        verify(flowFiber).suspend(any<FlowIORequest.SendAndReceive>())
    }

    @Test
    fun `calling receive with an initiating session will cause the flow to suspend to initiate the session`() {
        val session = createInitiatingSession()
        session.receive(String::class.java)

        val flowIORequestCapture = argumentCaptor<FlowIORequest<*>>()

        verify(flowFiber, times(1)).suspend(flowIORequestCapture.capture())

        assertTrue(flowIORequestCapture.firstValue is FlowIORequest.Receive)
    }

    @Test
    fun `calling receive with an initiated session will not cause the flow to suspend to initiate the session`() {
        val session = createInitiatedSession()
        session.receive(String::class.java)
        verify(flowFiber).suspend(any<FlowIORequest.Receive>())
    }

    @Test
    fun `receiving the wrong object type in receive throws an exception`() {
        whenever(serializationService.deserializeAndCheckType(eq(HELLO_THERE.toByteArray()), any<Class<*>>()))
            .thenThrow(DeserializedWrongAMQPObjectException(String::class.java, Int::class.java, 1, "wrong"))

        val session = createInitiatedSession()
        assertThrows<CordaRuntimeException> { session.receive(String::class.java) }
    }

    @Test
    fun `receive returns the result of the flow's suspension`() {
        val session = createInitiatedSession()
        assertEquals(HELLO_THERE, session.receive(String::class.java))
        verify(flowFiber).suspend(any<FlowIORequest.Receive>())
    }

    @Test
    fun `calling send with an initiating session will cause the flow to suspend to initiate the session`() {
        val session = createInitiatingSession()
        session.send(HI)

        val flowIORequestCapture = argumentCaptor<FlowIORequest<*>>()

        verify(flowFiber, times(1)).suspend(flowIORequestCapture.capture())

        assertTrue(flowIORequestCapture.firstValue is FlowIORequest.Send)
    }

    @Test
    fun `calling send with an initiated session will not cause the flow to suspend to initiate the session`() {
        val session = createInitiatedSession()
        session.send(HI)
        verify(flowFiber).suspend(any<FlowIORequest.Send>())
    }

    @Test
    fun `send suspends the fiber to send a message`() {
        val session = createInitiatedSession()
        session.send(HI)
        verify(flowFiber).suspend(any<FlowIORequest.Send>())
    }

    @Test
    fun `initiating session exposes context passed to constructor`() {
        val session = createInitiatingSession()

        assertEquals(platformValue, session.contextProperties[platformKey])
        assertEquals(userValue, session.contextProperties[userKey])
    }

    private fun createInitiatedSession() = FlowSessionImpl(
        counterparty = ALICE_X500_NAME,
        sourceSessionId = SESSION_ID,
        mockFlowFiberService,
        serializationService,
        flowContext,
        FlowSessionImpl.Direction.INITIATED_SIDE
    )

    private fun createInitiatingSession() = FlowSessionImpl(
        counterparty = ALICE_X500_NAME,
        sourceSessionId = SESSION_ID,
        mockFlowFiberService,
        serializationService,
        flowContext,
        FlowSessionImpl.Direction.INITIATING_SIDE
    )

}
