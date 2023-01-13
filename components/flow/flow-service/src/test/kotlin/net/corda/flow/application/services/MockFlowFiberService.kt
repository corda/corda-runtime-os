package net.corda.flow.application.services

import net.corda.flow.BOB_X500_NAME
import net.corda.flow.fiber.FlowFiber
import net.corda.flow.fiber.FlowFiberExecutionContext
import net.corda.flow.fiber.FlowFiberService
import net.corda.flow.pipeline.sandbox.FlowSandboxGroupContext
import net.corda.flow.pipeline.sandbox.SandboxDependencyInjector
import net.corda.flow.state.ContextPlatformProperties
import net.corda.flow.state.FlowCheckpoint
import net.corda.flow.state.FlowContext
import net.corda.flow.state.FlowStack
import net.corda.membership.read.MembershipGroupReader
import net.corda.sandboxgroupcontext.CurrentSandboxGroupContext
import net.corda.serialization.checkpoint.CheckpointSerializer
import net.corda.virtualnode.HoldingIdentity
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class MockFlowFiberService : FlowFiberService {
    val flowFiber = mock<FlowFiber>()
    private val sandboxDependencyInjector = mock<SandboxDependencyInjector>()
    val flowCheckpoint = mock<FlowCheckpoint>()
    val flowStack = mock<FlowStack>()
    private val checkpointSerializer = mock<CheckpointSerializer>()
    private val currentSandboxGroupContext = mock<CurrentSandboxGroupContext>()
    val sandboxGroupContext = mock<FlowSandboxGroupContext>()
    val holdingIdentity = HoldingIdentity(BOB_X500_NAME, "group1")
    private val membershipGroupReader = mock<MembershipGroupReader>()
    val flowFiberExecutionContext: FlowFiberExecutionContext

    val userKey = "userKey"
    val userValue = "userValue"

    val platformKey = "platformKey"
    val platformValue = "platformValue"

    val userContext = mapOf(userKey to userValue)
    val platformContext = mapOf(platformKey to platformValue)

    private val flowContext = mock<FlowContext>()
    private val platformProperties = mock<ContextPlatformProperties>()

    init {
        /**
         * Order here is important we need to set up the flow stack response
         * before we create the instance of the context
         */
        whenever(flowCheckpoint.flowStack).thenReturn(flowStack)
        whenever(sandboxGroupContext.dependencyInjector).thenReturn(sandboxDependencyInjector)
        whenever(sandboxGroupContext.checkpointSerializer).thenReturn(checkpointSerializer)
        whenever(currentSandboxGroupContext.get()).thenReturn(sandboxGroupContext)

        flowFiberExecutionContext = FlowFiberExecutionContext(
            flowCheckpoint,
            sandboxGroupContext,
            holdingIdentity,
            membershipGroupReader,
            currentSandboxGroupContext,
            emptyMap()
        )

        whenever(flowFiber.getExecutionContext()).thenReturn(flowFiberExecutionContext)

        whenever(flowContext.flattenUserProperties()).thenReturn(userContext)
        whenever(flowContext.flattenPlatformProperties()).thenReturn(platformContext)
        whenever(flowContext.platformProperties).thenReturn(platformProperties)
        whenever(flowCheckpoint.flowContext).thenReturn(flowContext)
    }

    override fun getExecutingFiber(): FlowFiber {
        return flowFiber
    }
}