package net.corda.flow.application.services

import net.corda.data.flow.FlowStackItem
import net.corda.data.identity.HoldingIdentity
import net.corda.flow.application.sessions.factory.FlowSessionFactory
import net.corda.flow.fiber.FlowFiber
import net.corda.flow.fiber.FlowFiberExecutionContext
import net.corda.flow.fiber.FlowFiberService
import net.corda.flow.fiber.FlowStackService
import net.corda.v5.application.flows.FlowSession
import net.corda.v5.base.exceptions.CordaRuntimeException
import net.corda.v5.base.types.MemberX500Name
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@Suppress("MaxLineLength")
class FlowMessagingImplTest {

    private companion object {
        const val FLOW_NAME = "flow name"
        val x500Name = MemberX500Name(
            commonName = "Alice",
            organisation = "Alice Corp",
            locality = "LDN",
            country = "GB"
        )
    }

    private val flowStackService = mock<FlowStackService>()

    private val flowFiberExecutionContext = FlowFiberExecutionContext(
        mock(),
        flowStackService,
        mock(),
        mock(),
        HoldingIdentity()
    )

    private val flowFiber = mock<FlowFiber<*>>().apply {
        whenever(getExecutionContext()).thenReturn(flowFiberExecutionContext)
    }

    private val flowFiberService = mock<FlowFiberService>().apply {
        whenever(getExecutingFiber()).thenReturn(flowFiber)
    }

    private val flowSession = mock<FlowSession>()

    private val flowSessionFactory = mock<FlowSessionFactory>().apply {
        whenever(create(any(), eq(x500Name), initiated = eq(false))).thenReturn(flowSession)
    }

    private val flowMessaging = FlowMessagingImpl(flowFiberService, flowSessionFactory)

    @Test
    fun `initiateFlow creates an uninitiated FlowSession when the current flow stack item represents an initiating flow`() {
        whenever(flowStackService.peek()).thenReturn(FlowStackItem(FLOW_NAME, true, mutableListOf()))
        flowMessaging.initiateFlow(x500Name)
        verify(flowSessionFactory).create(any(), eq(x500Name), initiated = eq(false))
    }

    @Test
    fun `initiateFlow adds the new session id to the current flow stack item (containing no sessions) when the item represents is an initiating flow`() {
        val flowStackItem = FlowStackItem(FLOW_NAME, true, mutableListOf())
        whenever(flowStackService.peek()).thenReturn(flowStackItem)
        flowMessaging.initiateFlow(x500Name)
        assertEquals(1, flowStackItem.sessionIds.size)
    }

    @Test
    fun `initiateFlow adds the new session id to the current flow stack item (containing existing sessions) when the item represents is an initiating flow`() {
        val flowStackItem = FlowStackItem(FLOW_NAME, true, mutableListOf("1", "2", "3"))
        whenever(flowStackService.peek()).thenReturn(flowStackItem)
        flowMessaging.initiateFlow(x500Name)
        assertEquals(4, flowStackItem.sessionIds.size)

    }

    @Test
    fun `initiateFlow throws an error when the current flow stack item represents a non-initiating flow`() {
        whenever(flowStackService.peek()).thenReturn(FlowStackItem(FLOW_NAME, false, emptyList()))
        assertThrows<CordaRuntimeException> { flowMessaging.initiateFlow(x500Name) }
    }

    @Test
    fun `initiateFlow throws an error if the flow stack is empty`() {
        whenever(flowStackService.peek()).thenReturn(null)
        assertThrows<CordaRuntimeException> { flowMessaging.initiateFlow(x500Name) }
    }
}