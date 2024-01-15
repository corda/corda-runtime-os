package net.corda.internal.serialization.amqp.helper

import net.corda.flow.fiber.FlowFiber
import net.corda.flow.fiber.FlowFiberExecutionContext
import net.corda.flow.fiber.FlowFiberService
import net.corda.flow.pipeline.metrics.FlowMetrics
import net.corda.flow.pipeline.sandbox.FlowSandboxGroupContext
import net.corda.flow.state.ContextPlatformProperties
import net.corda.flow.state.FlowCheckpoint
import net.corda.flow.state.FlowContext
import net.corda.membership.read.MembershipGroupReader
import net.corda.sandboxgroupcontext.CurrentSandboxGroupContext
import net.corda.v5.application.flows.FlowContextPropertyKeys
import net.corda.v5.base.types.MemberX500Name
import net.corda.v5.serialization.SingletonSerializeAsToken
import net.corda.virtualnode.HoldingIdentity
import org.mockito.Mockito
import org.mockito.Mockito.mock
import org.mockito.kotlin.whenever

class TestFlowFiberServiceWithSerialization(
    currentSandboxGroupContext: CurrentSandboxGroupContext
) : FlowFiberService, SingletonSerializeAsToken {
    private val mockFlowFiber = mock(FlowFiber::class.java)
    private val mockFlowSandboxGroupContext = mock(FlowSandboxGroupContext::class.java)
    private val membershipGroupReader = mock(MembershipGroupReader::class.java)
    private val mockFlowCheckpoint = mock(FlowCheckpoint::class.java).also {
        whenever(it.flowContext).thenReturn(object : FlowContext {
            override fun flattenPlatformProperties(): Map<String, String> {
                throw NotImplementedError("Not envisioned to be invoked in tests.")
            }

            override fun flattenUserProperties(): Map<String, String> {
                throw NotImplementedError("Not envisioned to be invoked in tests.")
            }

            override val platformProperties: ContextPlatformProperties
                get() = throw NotImplementedError("Not envisioned to be invoked in tests.")

            override fun put(key: String, value: String) {
                throw NotImplementedError("Not envisioned to be invoked in tests.")
            }

            override fun get(key: String): String =
                when (key) {
                    FlowContextPropertyKeys.CPI_NAME -> "Cordapp1"
                    FlowContextPropertyKeys.CPI_VERSION -> "1"
                    FlowContextPropertyKeys.CPI_SIGNER_SUMMARY_HASH -> "hash1234"
                    else -> "1213213213" //FlowContextPropertyKeys.CPI_FILE_CHECKSUM
                }
        })
    }

    init {
        val bobX500 = "CN=Bob, O=Bob Corp, L=LDN, C=GB"
        val bobX500Name = MemberX500Name.parse(bobX500)
        val holdingIdentity = HoldingIdentity(bobX500Name, "group1")
        val flowFiberExecutionContext = FlowFiberExecutionContext(
            mockFlowCheckpoint,
            mockFlowSandboxGroupContext,
            holdingIdentity,
            membershipGroupReader,
            currentSandboxGroupContext,
            emptyMap(),
            mock(FlowMetrics::class.java),
            emptyMap()
        )

        Mockito.`when`(mockFlowFiber.getExecutionContext()).thenReturn(flowFiberExecutionContext)
    }

    override fun getExecutingFiber(): FlowFiber {
        return mockFlowFiber
    }
}
