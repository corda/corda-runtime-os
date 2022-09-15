package net.corda.internal.serialization.amqp.helper

import net.corda.flow.fiber.FlowFiber
import net.corda.flow.fiber.FlowFiberExecutionContext
import net.corda.flow.fiber.FlowFiberService
import net.corda.flow.pipeline.sandbox.FlowSandboxGroupContext
import net.corda.flow.state.FlowCheckpoint
import net.corda.internal.serialization.amqp.SerializerFactory
import net.corda.membership.read.MembershipGroupReader
import net.corda.v5.base.types.MemberX500Name
import net.corda.v5.cipher.suite.CipherSchemeMetadata
import net.corda.v5.serialization.SingletonSerializeAsToken
import net.corda.virtualnode.HoldingIdentity
import org.mockito.Mockito
import org.mockito.Mockito.mock

class TestFlowFiberServiceWithSerialization : FlowFiberService, SingletonSerializeAsToken {
    private val mockFlowFiber: FlowFiber
    private val mockFlowSandboxGroupContext = mock(FlowSandboxGroupContext::class.java)

    init{
        val membershipGroupReader: MembershipGroupReader = mock(MembershipGroupReader::class.java)
        val bobX500 = "CN=Bob, O=Bob Corp, L=LDN, C=GB"
        val bobX500Name = MemberX500Name.parse(bobX500)
        val holdingIdentity =  HoldingIdentity(bobX500Name,"group1")
        val flowFiberExecutionContext = FlowFiberExecutionContext(
            mock(FlowCheckpoint::class.java),
            mockFlowSandboxGroupContext,
            holdingIdentity,
            membershipGroupReader
        )

        mockFlowFiber = mock(FlowFiber::class.java)
        Mockito.`when`(mockFlowFiber.getExecutionContext()).thenReturn(flowFiberExecutionContext)

    }
    override fun getExecutingFiber(): FlowFiber {
        return mockFlowFiber
    }

    fun configureSerializer(registerMoreSerializers: (it: SerializerFactory) -> Unit, schemeMetadata: CipherSchemeMetadata){
        val serializer = TestSerializationService.getTestSerializationService(registerMoreSerializers, schemeMetadata)
        Mockito.`when`(mockFlowSandboxGroupContext.amqpSerializer).thenReturn(serializer)
    }
}