package net.corda.flow.application.sessions

import net.corda.flow.BOB_X500_NAME
import net.corda.flow.application.serialization.SerializationServiceInternal
import net.corda.flow.application.services.MockFlowFiberService
import net.corda.flow.application.sessions.factory.FlowSessionFactoryImpl
import net.corda.flow.fiber.FlowIORequest
import net.corda.flow.state.asFlowContext
import net.corda.v5.application.messaging.FlowContextPropertiesBuilder
import net.corda.v5.base.exceptions.CordaRuntimeException
import net.corda.v5.serialization.SerializedBytes
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class FlowSessionFactoryImplTest {

    private companion object {
        const val SESSION_ID = "session id"
        const val HI = "hi"
    }

    private val contextMap = mapOf("key" to "value")

    private val mockFlowFiberService = MockFlowFiberService()
    private val flowFiber = mockFlowFiberService.flowFiber
    private val serializationService = mock<SerializationServiceInternal>()
    private val flowSessionFactory = FlowSessionFactoryImpl(mockFlowFiberService, serializationService)

    @Suppress("Unused")
    @BeforeEach
    fun setup() {
        serializationService.apply {
            whenever(serialize(HI)).thenReturn(SerializedBytes(HI.toByteArray()))
        }
    }

    @Test
    fun `can create an initiated flow session`() {
        val session = flowSessionFactory.createInitiatedFlowSession(SESSION_ID, BOB_X500_NAME, contextMap)
        assertEquals(BOB_X500_NAME, session.counterparty)
        assertEquals("value", session.contextProperties["key"])
        session.send(HI)
        verify(flowFiber).suspend(any<FlowIORequest.Send>())
    }

    @Test
    fun `can create an initiating flow session`() {
        val session = flowSessionFactory.createInitiatingFlowSession(SESSION_ID, BOB_X500_NAME, null)
        assertEquals(BOB_X500_NAME, session.counterparty)
        session.send(HI)
        verify(flowFiber).suspend(any<FlowIORequest.Send>())
    }

    @Test
    fun `can create an initiating flow session with a context property builder`() {
        val contextBuilder = mock<FlowContextPropertiesBuilder>()
        val session = flowSessionFactory.createInitiatingFlowSession(SESSION_ID, BOB_X500_NAME, contextBuilder)
        assertEquals(BOB_X500_NAME, session.counterparty)
        session.send(HI)
        verify(contextBuilder).apply(any())
        verify(flowFiber).suspend(any<FlowIORequest.Send>())
    }

    @Test
    fun `initiated sessions are given immutable context`() {
        val session = flowSessionFactory.createInitiatedFlowSession(SESSION_ID, BOB_X500_NAME, contextMap)
        assertEquals("value", session.contextProperties["key"])
        assertThrows<CordaRuntimeException> { session.contextProperties.put("key2", "value2") }

        // Platform context via the Corda internal extension function should also be immutable
        assertThrows<CordaRuntimeException> {
            session.contextProperties.asFlowContext.platformProperties["key2"] = "value2"
        }
    }

    @Test
    fun `initiating sessions are given immutable context pulled from Corda when no builder is passed`() {
        val session = flowSessionFactory.createInitiatingFlowSession(SESSION_ID, BOB_X500_NAME, null)

        assertEquals(mockFlowFiberService.platformValue, session.contextProperties[mockFlowFiberService.platformKey])
        assertEquals(mockFlowFiberService.userValue, session.contextProperties[mockFlowFiberService.userKey])

        // Immutable
        assertThrows<CordaRuntimeException> { session.contextProperties.put("key2", "value2") }

        // Platform context via the Corda internal extension function should also be immutable
        assertThrows<CordaRuntimeException> {
            session.contextProperties.asFlowContext.platformProperties["key2"] = "value2"
        }
    }

    @Test
    fun `can create an initiating session and mutate its context with a builder`() {
        val session =
            flowSessionFactory.createInitiatingFlowSession(SESSION_ID, BOB_X500_NAME) { flowContextProperties ->
                // Additional user context
                flowContextProperties.put("extraUserKey", "extraUserValue")
                // Addition platform context via the Corda internal extension function
                flowContextProperties.asFlowContext.platformProperties["extraPlatformKey"] = "extraPlatformValue"
            }

        // Validate mutated
        assertEquals("extraUserValue", session.contextProperties["extraUserKey"])
        assertEquals("extraPlatformValue", session.contextProperties["extraPlatformKey"])

        // Validate from Corda
        assertEquals(mockFlowFiberService.platformValue, session.contextProperties[mockFlowFiberService.platformKey])
        assertEquals(mockFlowFiberService.userValue, session.contextProperties[mockFlowFiberService.userKey])

        // The session context properties (user and platform) should be immutable once set
        assertThrows<CordaRuntimeException> { session.contextProperties.put("key2", "value2") }
        assertThrows<CordaRuntimeException> {
            session.contextProperties.asFlowContext.platformProperties["key2"] = "value2"
        }

        // Verify the mutated context makes it into the request
        session.send(HI)

        val flowIORequestCapture = argumentCaptor<FlowIORequest<*>>()
        verify(flowFiber, times(1)).suspend(flowIORequestCapture.capture())
    }
}