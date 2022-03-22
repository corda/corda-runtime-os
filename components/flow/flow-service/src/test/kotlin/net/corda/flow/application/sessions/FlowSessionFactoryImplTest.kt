package net.corda.flow.application.sessions

import net.corda.flow.application.sessions.factory.FlowSessionFactoryImpl
import net.corda.flow.fiber.FlowFiber
import net.corda.flow.fiber.FlowFiberService
import net.corda.flow.fiber.FlowIORequest
import net.corda.v5.base.types.MemberX500Name
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
        val x500Name = MemberX500Name(
            commonName = "Alice",
            organisation = "Alice Corp",
            locality = "LDN",
            country = "GB"
        )
        val received = mapOf(SESSION_ID to "hello there")
    }

    private val flowFiber = mock<FlowFiber<*>>().apply {
        whenever(suspend(any<FlowIORequest.SendAndReceive>())).thenReturn(received)
        whenever(suspend(any<FlowIORequest.Receive>())).thenReturn(received)
    }

    private val flowFiberService = mock<FlowFiberService>().apply {
        whenever(getExecutingFiber()).thenReturn(flowFiber)
    }

    private val flowSessionFactory = FlowSessionFactoryImpl(flowFiberService)

    @Test
    fun `Passing in initiated = true creates an initiated flow session`() {
        val session = flowSessionFactory.create(SESSION_ID, x500Name, initiated = true)
        assertEquals(x500Name, session.counterparty)
        session.send("hi")
        verify(flowFiber, never()).suspend(any<FlowIORequest.InitiateFlow>())
    }

    @Test
    fun `Passing in initiated = false creates an uninitiated flow session`() {
        val session = flowSessionFactory.create(SESSION_ID, x500Name, initiated = false)
        assertEquals(x500Name, session.counterparty)
        session.send("hi")
        verify(flowFiber).suspend(any<FlowIORequest.InitiateFlow>())
    }
}