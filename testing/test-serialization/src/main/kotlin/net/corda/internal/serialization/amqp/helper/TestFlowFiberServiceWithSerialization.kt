package net.corda.internal.serialization.amqp.helper

import net.corda.flow.fiber.FlowFiber
import net.corda.flow.fiber.FlowFiberExecutionContext
import net.corda.flow.fiber.FlowFiberService
import net.corda.flow.pipeline.sandbox.FlowSandboxGroupContext
import net.corda.flow.state.FlowCheckpoint
import net.corda.membership.read.MembershipGroupReader
import net.corda.sandboxgroupcontext.CurrentSandboxGroupContext
import net.corda.v5.base.types.MemberX500Name
import net.corda.v5.serialization.SingletonSerializeAsToken
import net.corda.virtualnode.HoldingIdentity
import org.mockito.Mockito
import org.mockito.Mockito.mock

class TestFlowFiberServiceWithSerialization(
    currentSandboxGroupContext: CurrentSandboxGroupContext
) : FlowFiberService, SingletonSerializeAsToken {
    private val mockFlowFiber = mock(FlowFiber::class.java)
    private val mockFlowSandboxGroupContext = mock(FlowSandboxGroupContext::class.java)
    private val membershipGroupReader = mock(MembershipGroupReader::class.java)

    init {
        val bobX500 = "CN=Bob, O=Bob Corp, L=LDN, C=GB"
        val bobX500Name = MemberX500Name.parse(bobX500)
        val holdingIdentity =  HoldingIdentity(bobX500Name,"group1")
        val flowFiberExecutionContext = FlowFiberExecutionContext(
            mock(FlowCheckpoint::class.java),
            mockFlowSandboxGroupContext,
            holdingIdentity,
            membershipGroupReader,
            currentSandboxGroupContext,
            emptyMap()
        )

        Mockito.`when`(mockFlowFiber.getExecutionContext()).thenReturn(flowFiberExecutionContext)
    }

    override fun getExecutingFiber(): FlowFiber {
        return mockFlowFiber
    }
}