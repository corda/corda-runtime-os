package net.corda.flow.application.services

import net.corda.flow.BOB_X500_NAME
import net.corda.flow.fiber.FlowFiber
import net.corda.flow.fiber.FlowFiberExecutionContext
import net.corda.flow.fiber.FlowFiberService
import net.corda.flow.pipeline.sandbox.FlowSandboxGroupContext
import net.corda.flow.pipeline.sandbox.SandboxDependencyInjector
import net.corda.flow.state.FlowCheckpoint
import net.corda.flow.state.FlowStack
import net.corda.membership.read.MembershipGroupReader
import net.corda.serialization.checkpoint.CheckpointSerializer
import net.corda.virtualnode.HoldingIdentity
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class MockFlowFiberService : FlowFiberService {
    val flowFiber = mock<FlowFiber>()
    private val sandboxDependencyInjector = mock<SandboxDependencyInjector>()
    val flowCheckpoint: FlowCheckpoint = mock()
    val flowStack: FlowStack = mock()
    private val checkpointSerializer = mock<CheckpointSerializer>()
    val sandboxGroupContext: FlowSandboxGroupContext = mock()
    val holdingIdentity: HoldingIdentity =  HoldingIdentity(BOB_X500_NAME.toString(),"group1")
    private val membershipGroupReader: MembershipGroupReader = mock()
    val flowFiberExecutionContext: FlowFiberExecutionContext

    init {
        /**
         * Order here is important we need to set up the flow stack response
         * before we create the instance of the context
         */
        whenever(flowCheckpoint.flowStack).thenReturn(flowStack)
        whenever(sandboxGroupContext.dependencyInjector).thenReturn(sandboxDependencyInjector)
        whenever(sandboxGroupContext.checkpointSerializer).thenReturn(checkpointSerializer)

        flowFiberExecutionContext = FlowFiberExecutionContext(
            flowCheckpoint,
            sandboxGroupContext,
            holdingIdentity,
            membershipGroupReader
        )

        whenever(flowFiber.getExecutionContext()).thenReturn(flowFiberExecutionContext)
    }

    override fun getExecutingFiber(): FlowFiber {
        return flowFiber
    }
}