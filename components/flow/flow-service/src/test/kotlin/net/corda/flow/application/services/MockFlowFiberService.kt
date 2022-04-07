package net.corda.flow.application.services

import net.corda.data.identity.HoldingIdentity
import net.corda.flow.BOB_X500_NAME
import net.corda.flow.fiber.FlowFiber
import net.corda.flow.fiber.FlowFiberExecutionContext
import net.corda.flow.fiber.FlowFiberService
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class MockFlowFiberService : FlowFiberService {
    val flowFiber = mock<FlowFiber<*>>()
    val flowFiberExecutionContext = FlowFiberExecutionContext(
        mock(),
        mock(),
        mock(),
        mock(),
        HoldingIdentity(BOB_X500_NAME.toString(), "group1"),
        mock()
    )

    init {
        whenever(flowFiber.getExecutionContext()).thenReturn(flowFiberExecutionContext)
    }

    override fun getExecutingFiber(): FlowFiber<*> {
        return flowFiber
    }
}