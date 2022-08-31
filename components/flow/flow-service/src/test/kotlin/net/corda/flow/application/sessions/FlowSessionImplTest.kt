package net.corda.flow.application.sessions

import net.corda.flow.ALICE_X500_NAME
import net.corda.flow.application.services.MockFlowFiberService
import net.corda.flow.fiber.FlowIORequest
import net.corda.flow.state.asFlowContext
import net.corda.v5.application.flows.set
import net.corda.v5.application.serialization.SerializationService
import net.corda.v5.base.exceptions.CordaRuntimeException
import net.corda.v5.serialization.SerializedBytes
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
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
            whenever(amqpSerializer).thenReturn(serializationService)
        }
    }

    @Test
    fun `calling sendAndReceive with an initiating session will cause the flow to suspend to initiate the session`() {
        val session = createInitiatingSession()
        session.sendAndReceive(String::class.java, HI)

        val flowIORequestCapture = argumentCaptor<FlowIORequest<*>>()

        verify(flowFiber, times(2)).suspend(flowIORequestCapture.capture())

        assertTrue(flowIORequestCapture.firstValue is FlowIORequest.InitiateFlow)
        assertTrue(flowIORequestCapture.secondValue is FlowIORequest.SendAndReceive)

        validateInitiateFlowRequest(flowIORequestCapture.firstValue as FlowIORequest.InitiateFlow)
    }

    @Test
    fun `calling sendAndReceive with an initiated session will not cause the flow to suspend to initiate the session`() {
        val session = createInitiatedSession()
        session.sendAndReceive(String::class.java, HI)
        verify(flowFiber, never()).suspend(any<FlowIORequest.InitiateFlow>())
        verify(flowFiber).suspend(any<FlowIORequest.SendAndReceive>())
    }

    @Test
    fun `receiving the wrong object type in sendAndReceive throws an exception`() {
        whenever(serializationService.deserialize(eq(HELLO_THERE.toByteArray()), any<Class<*>>())).thenReturn(1)
        val session = createInitiatedSession()
        assertThrows<CordaRuntimeException> { session.sendAndReceive(String::class.java, HI) }
    }

    @Test
    fun `sendAndReceive returns the result of the flow's suspension`() {
        val session = createInitiatedSession()
        assertEquals(HELLO_THERE, session.sendAndReceive(String::class.java, HI).unwrap { it })
        verify(flowFiber).suspend(any<FlowIORequest.SendAndReceive>())
    }

    @Test
    fun `calling receive with an initiating session will cause the flow to suspend to initiate the session`() {
        val session = createInitiatingSession()
        session.receive(String::class.java)

        val flowIORequestCapture = argumentCaptor<FlowIORequest<*>>()

        verify(flowFiber, times(2)).suspend(flowIORequestCapture.capture())

        assertTrue(flowIORequestCapture.firstValue is FlowIORequest.InitiateFlow)
        assertTrue(flowIORequestCapture.secondValue is FlowIORequest.Receive)

        validateInitiateFlowRequest(flowIORequestCapture.firstValue as FlowIORequest.InitiateFlow)
    }

    @Test
    fun `calling receive with an initiated session will not cause the flow to suspend to initiate the session`() {
        val session = createInitiatedSession()
        session.receive(String::class.java)
        verify(flowFiber, never()).suspend(any<FlowIORequest.InitiateFlow>())
        verify(flowFiber).suspend(any<FlowIORequest.Receive>())
    }

    @Test
    fun `receiving the wrong object type in receive throws an exception`() {
        whenever(serializationService.deserialize(eq(HELLO_THERE.toByteArray()), any<Class<*>>())).thenReturn(1)
        val session = createInitiatedSession()
        assertThrows<CordaRuntimeException> { session.receive(String::class.java) }
    }

    @Test
    fun `receive returns the result of the flow's suspension`() {
        val session = createInitiatedSession()
        assertEquals(HELLO_THERE, session.receive(String::class.java).unwrap { it })
        verify(flowFiber).suspend(any<FlowIORequest.Receive>())
    }

    @Test
    fun `calling send with an initiating session will cause the flow to suspend to initiate the session`() {
        val session = createInitiatingSession()
        session.send(HI)

        val flowIORequestCapture = argumentCaptor<FlowIORequest<*>>()

        verify(flowFiber, times(2)).suspend(flowIORequestCapture.capture())

        assertTrue(flowIORequestCapture.firstValue is FlowIORequest.InitiateFlow)
        assertTrue(flowIORequestCapture.secondValue is FlowIORequest.Send)

        validateInitiateFlowRequest(flowIORequestCapture.firstValue as FlowIORequest.InitiateFlow)
    }

    @Test
    fun `calling send with an initiated session will not cause the flow to suspend to initiate the session`() {
        val session = createInitiatedSession()
        session.send(HI)
        verify(flowFiber, never()).suspend(any<FlowIORequest.InitiateFlow>())
        verify(flowFiber).suspend(any<FlowIORequest.Send>())
    }

    @Test
    fun `send suspends the fiber to send a message`() {
        val session = createInitiatedSession()
        session.send(HI)
        verify(flowFiber).suspend(any<FlowIORequest.Send>())
    }

    @Test
    fun `initiated sessions have immutable context`() {
        val session = createInitiatedSession()
        assertEquals("value", session.contextProperties["key"])
        assertThrows<CordaRuntimeException> { session.contextProperties["key2"] = "value2" }

        // Platform context via the Corda internal extension function should also be immutable
        assertThrows<CordaRuntimeException> {
            session.contextProperties.asFlowContext.platformProperties["key2"] = "value2"
        }
    }

    @Test
    fun `initiating sessions pull initial context from the platform`() {
        val session = createInitiatingSession()

        assertEquals(mockFlowFiberService.PLATFORM_VALUE, session.contextProperties[mockFlowFiberService.PLATFORM_KEY])
        assertEquals(mockFlowFiberService.USER_VALUE, session.contextProperties[mockFlowFiberService.USER_KEY])

        // Ensure user keys can be overwritten
        session.contextProperties[mockFlowFiberService.USER_KEY] = "overwriteUser"
        assertEquals("overwriteUser", session.contextProperties[mockFlowFiberService.USER_KEY])

        // And platform keys cannot
        assertThrows<java.lang.IllegalArgumentException> {
            session.contextProperties[mockFlowFiberService.PLATFORM_KEY] = "overwritePlatform"
        }
    }

    @Test
    fun `initiating sessions have mutable context`() {
        val session = createInitiatingSession()
        // Additional user context
        session.contextProperties["extraUserKey"] = "extraUserValue"
        assertEquals("extraUserValue", session.contextProperties["extraUserKey"])
        // Addition platform context via the Corda internal extension function
        session.contextProperties.asFlowContext.platformProperties["extraPlatformKey"] = "extraPlatformValue"
        assertEquals("extraPlatformValue", session.contextProperties["extraPlatformKey"])

        // Verify the mutated context makes it into the request
        session.send(HI)

        val flowIORequestCapture = argumentCaptor<FlowIORequest<*>>()
        verify(flowFiber, times(2)).suspend(flowIORequestCapture.capture())

        val mutatedUserMap = mockFlowFiberService.userContext.toMutableMap()
        mutatedUserMap["extraUserKey"] = "extraUserValue"

        val mutatedPlatformMap = mockFlowFiberService.platformContext.toMutableMap()
        mutatedPlatformMap["extraPlatformKey"] = "extraPlatformValue"

        with(flowIORequestCapture.firstValue as FlowIORequest.InitiateFlow) {
            assertThat(contextUserProperties).isEqualTo(mutatedUserMap)
            assertThat(contextPlatformProperties).isEqualTo(mutatedPlatformMap)
        }
    }

    private fun createInitiatedSession(): FlowSessionImpl = FlowSessionImpl.asInitiatedSession(
        counterparty = ALICE_X500_NAME,
        sourceSessionId = SESSION_ID,
        mockFlowFiberService,
        mapOf("key" to "value")
    )

    private fun createInitiatingSession() = FlowSessionImpl.asInitiatingSession(
        counterparty = ALICE_X500_NAME,
        sourceSessionId = SESSION_ID,
        mockFlowFiberService,
    )

    private fun validateInitiateFlowRequest(request: FlowIORequest.InitiateFlow) {
        with(request) {
            assertThat(contextUserProperties).isEqualTo(mockFlowFiberService.userContext)
            assertThat(contextPlatformProperties).isEqualTo(mockFlowFiberService.platformContext)
            assertThat(sessionId).isEqualTo(SESSION_ID)
            assertThat(x500Name).isEqualTo(ALICE_X500_NAME)
        }
    }
}
