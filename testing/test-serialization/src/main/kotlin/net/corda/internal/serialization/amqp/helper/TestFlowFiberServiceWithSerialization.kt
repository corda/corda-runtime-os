package net.corda.internal.serialization.amqp.helper

import net.corda.flow.fiber.FlowFiber
import net.corda.flow.fiber.FlowFiberExecutionContext
import net.corda.flow.fiber.FlowFiberService
import net.corda.flow.pipeline.sandbox.FlowSandboxGroupContext
import net.corda.flow.state.FlowCheckpoint
import net.corda.internal.serialization.amqp.SerializerFactory
import net.corda.membership.read.MembershipGroupReader
import net.corda.sandboxgroupcontext.SandboxGroupContextService
import net.corda.v5.base.types.MemberX500Name
import net.corda.v5.cipher.suite.CipherSchemeMetadata
import net.corda.v5.serialization.SingletonSerializeAsToken
import net.corda.virtualnode.HoldingIdentity
import org.mockito.Mockito
import org.mockito.Mockito.mock
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class TestFlowFiberServiceWithSerialization : FlowFiberService, SingletonSerializeAsToken {
    private val mockFlowFiber = mock<FlowFiber>()
    private val mockFlowSandboxGroupContext = mock<FlowSandboxGroupContext>()
    private val membershipGroupReader = mock<MembershipGroupReader>()
    private val sandboxGroupContextService = mock<SandboxGroupContextService>()

    init {
        val bobX500 = "CN=Bob, O=Bob Corp, L=LDN, C=GB"
        val bobX500Name = MemberX500Name.parse(bobX500)
        val holdingIdentity = HoldingIdentity(bobX500Name, "group1")
        val flowFiberExecutionContext = FlowFiberExecutionContext(
            mock(FlowCheckpoint::class.java),
            mockFlowSandboxGroupContext,
            holdingIdentity,
            membershipGroupReader,
            sandboxGroupContextService
        )

        whenever(sandboxGroupContextService.getCurrent()).thenReturn(mockFlowSandboxGroupContext)
        whenever(mockFlowFiber.getExecutionContext()).thenReturn(flowFiberExecutionContext)

    }

    override fun getExecutingFiber(): FlowFiber {
        return mockFlowFiber
    }

    fun configureSerializer(registerMoreSerializers: (it: SerializerFactory) -> Unit, schemeMetadata: CipherSchemeMetadata) {
        val serializer = TestSerializationService.getTestSerializationService(registerMoreSerializers, schemeMetadata)
        whenever(mockFlowSandboxGroupContext.amqpSerializer).thenReturn(serializer)
    }
}