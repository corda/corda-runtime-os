package net.corda.flow.application.sessions

import net.corda.flow.BOB_X500_NAME
import net.corda.flow.application.services.MockFlowFiberService
import net.corda.flow.application.sessions.factory.FlowSessionFactoryImpl
import net.corda.flow.fiber.FlowFiberSerializationService
import net.corda.flow.fiber.FlowIORequest
import net.corda.v5.application.messaging.FlowContextPropertiesBuilder
import net.corda.v5.serialization.SerializedBytes
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class FlowSessionFactoryImplTest {

    private companion object {
        const val SESSION_ID = "session id"
        const val HI = "hi"
    }

    private val mockFlowFiberService = MockFlowFiberService()
    private val flowFiber = mockFlowFiberService.flowFiber
    private val flowFiberSerializationService = mock<FlowFiberSerializationService>()
    private val flowSessionFactory = FlowSessionFactoryImpl(mockFlowFiberService, flowFiberSerializationService)

    @Suppress("Unused")
    @BeforeEach
    fun setup() {
        flowFiberSerializationService.apply {
            whenever(serialize(HI)).thenReturn(SerializedBytes(HI.toByteArray()))
        }
    }

    @Test
    fun `can create an initiated flow session`() {
        val session = flowSessionFactory.createInitiatedFlowSession(SESSION_ID, BOB_X500_NAME, mapOf("key" to "value"))
        assertEquals(BOB_X500_NAME, session.counterparty)
        assertEquals("value", session.contextProperties["key"])
        session.send(HI)
        verify(flowFiber, never()).suspend(any<FlowIORequest.InitiateFlow>())
    }

    @Test
    fun `can create an initiating flow session`() {
        val session = flowSessionFactory.createInitiatingFlowSession(SESSION_ID, BOB_X500_NAME, null)
        assertEquals(BOB_X500_NAME, session.counterparty)
        session.send(HI)
        verify(flowFiber).suspend(any<FlowIORequest.InitiateFlow>())
    }

    @Test
    fun `can create an initiating flow session with a context property builder`() {
        val contextBuilder = mock<FlowContextPropertiesBuilder>()
        val session = flowSessionFactory.createInitiatingFlowSession(SESSION_ID, BOB_X500_NAME, contextBuilder)
        assertEquals(BOB_X500_NAME, session.counterparty)
        session.send(HI)
        verify(contextBuilder).apply(any())
        verify(flowFiber).suspend(any<FlowIORequest.InitiateFlow>())
    }
}