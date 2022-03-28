package net.corda.flow.application.sessions

import net.corda.flow.BOB_X500_HOLDING_IDENTITY
import net.corda.flow.BOB_X500_NAME
import net.corda.flow.application.sessions.factory.FlowSessionFactoryImpl
import net.corda.flow.fiber.FlowFiber
import net.corda.flow.fiber.FlowFiberExecutionContext
import net.corda.flow.fiber.FlowFiberService
import net.corda.flow.fiber.FlowIORequest
import net.corda.flow.pipeline.sandbox.FlowSandboxContextTypes
import net.corda.sandboxgroupcontext.SandboxGroupContext
import net.corda.v5.application.services.serialization.SerializationService
import net.corda.v5.serialization.SerializedBytes
import org.junit.jupiter.api.Assertions.assertEquals
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

    private val serializationService = mock<SerializationService>().apply {
        whenever(serialize(HI)).thenReturn(SerializedBytes(HI.toByteArray()))
    }

    private val sandboxGroupContext = mock<SandboxGroupContext>().apply {
        whenever(get(FlowSandboxContextTypes.AMQP_P2P_SERIALIZATION_SERVICE, SerializationService::class.java))
            .thenReturn(serializationService)
    }

    private val flowFiberExecutionContext =
        FlowFiberExecutionContext(mock(), mock(), mock(), sandboxGroupContext, BOB_X500_HOLDING_IDENTITY, mock())

    private val flowFiber = mock<FlowFiber<*>>().apply {
        whenever(getExecutionContext()).thenReturn(flowFiberExecutionContext)
    }

    private val flowFiberService = mock<FlowFiberService>().apply {
        whenever(getExecutingFiber()).thenReturn(flowFiber)
    }

    private val flowSessionFactory = FlowSessionFactoryImpl(flowFiberService)

    @Test
    fun `Passing in initiated = true creates an initiated flow session`() {
        val session = flowSessionFactory.create(SESSION_ID, BOB_X500_NAME, initiated = true)
        assertEquals(BOB_X500_NAME, session.counterparty)
        session.send(HI)
        verify(flowFiber, never()).suspend(any<FlowIORequest.InitiateFlow>())
    }

    @Test
    fun `Passing in initiated = false creates an uninitiated flow session`() {
        val session = flowSessionFactory.create(SESSION_ID, BOB_X500_NAME, initiated = false)
        assertEquals(BOB_X500_NAME, session.counterparty)
        session.send(HI)
        verify(flowFiber).suspend(any<FlowIORequest.InitiateFlow>())
    }
}