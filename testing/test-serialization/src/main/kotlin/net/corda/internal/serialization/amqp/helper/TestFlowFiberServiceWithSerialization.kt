package net.corda.internal.serialization.amqp.helper

import net.corda.flow.fiber.FlowFiber
import net.corda.flow.fiber.FlowFiberExecutionContext
import net.corda.flow.fiber.FlowFiberService
import net.corda.flow.pipeline.sandbox.FlowSandboxGroupContext
import net.corda.flow.state.FlowCheckpoint
import net.corda.internal.serialization.amqp.SerializerFactory
import net.corda.libs.packaging.core.CordappManifest
import net.corda.libs.packaging.core.CordappType
import net.corda.libs.packaging.core.CpkFormatVersion
import net.corda.libs.packaging.core.CpkIdentifier
import net.corda.libs.packaging.core.CpkManifest
import net.corda.libs.packaging.core.CpkMetadata
import net.corda.libs.packaging.core.CpkType
import net.corda.membership.read.MembershipGroupReader
import net.corda.sandbox.SandboxGroup
import net.corda.v5.base.types.MemberX500Name
import net.corda.sandboxgroupcontext.CurrentSandboxGroupContext
import net.corda.v5.cipher.suite.CipherSchemeMetadata
import net.corda.v5.crypto.DigestAlgorithmName
import net.corda.v5.crypto.SecureHash
import net.corda.v5.serialization.SingletonSerializeAsToken
import net.corda.virtualnode.HoldingIdentity
import org.mockito.Mockito
import org.mockito.Mockito.mock
import org.osgi.framework.Bundle
import java.time.Instant

class TestFlowFiberServiceWithSerialization : FlowFiberService, SingletonSerializeAsToken {
    private val mockFlowFiber = mock(FlowFiber::class.java)
    private val mockFlowSandboxGroupContext = mock(FlowSandboxGroupContext::class.java)
    private val membershipGroupReader = mock(MembershipGroupReader::class.java)
    private val currentSandboxGroupContext = mock(CurrentSandboxGroupContext::class.java)
    private val mockSandboxGroup = mock(SandboxGroup::class.java)

    init {
        val bobX500 = "CN=Bob, O=Bob Corp, L=LDN, C=GB"
        val bobX500Name = MemberX500Name.parse(bobX500)
        val holdingIdentity =  HoldingIdentity(bobX500Name,"group1")
        val mockSandboxGroup = mock(SandboxGroup::class.java)
        Mockito.`when`(mockSandboxGroup.metadata).thenReturn(mockCpkMetadata())
        Mockito.`when`(mockSandboxGroup.getEvolvableTag(MockitoHelper.anyObject())).thenReturn("E;bundle;sandbox")
        Mockito.`when`(mockFlowSandboxGroupContext.sandboxGroup).thenReturn(mockSandboxGroup)
        val flowFiberExecutionContext = FlowFiberExecutionContext(
            mock(FlowCheckpoint::class.java),
            mockFlowSandboxGroupContext,
            holdingIdentity,
            membershipGroupReader,
            currentSandboxGroupContext,
            emptyMap()
        )

        Mockito.`when`(currentSandboxGroupContext.get()).thenReturn(mockFlowSandboxGroupContext)
        Mockito.`when`(mockFlowFiber.getExecutionContext()).thenReturn(flowFiberExecutionContext)
        Mockito.`when`(mockSandboxGroup.metadata).thenReturn(emptyMap())
        Mockito.`when`(mockFlowSandboxGroupContext.sandboxGroup).thenReturn(mockSandboxGroup)
    }

    override fun getExecutingFiber(): FlowFiber {
        return mockFlowFiber
    }

    fun configureSerializer(registerMoreSerializers: (it: SerializerFactory) -> Unit, schemeMetadata: CipherSchemeMetadata) {
        val serializer = TestSerializationService.getTestSerializationService(registerMoreSerializers, schemeMetadata)
        whenever(mockFlowSandboxGroupContext.amqpSerializer).thenReturn(serializer)
    }

    private fun mockCpkMetadata() = mapOf(
        mock(Bundle::class.java) to makeCpkMetadata(1, CordappType.CONTRACT),
        mock(Bundle::class.java) to makeCpkMetadata(2, CordappType.WORKFLOW),
        mock(Bundle::class.java) to makeCpkMetadata(3, CordappType.CONTRACT),
    )

    private fun makeCpkMetadata(i: Int, cordappType: CordappType) = CpkMetadata(
        CpkIdentifier("MockCpk", "$i", null),
        CpkManifest(CpkFormatVersion(1, 1)),
        "mock-bundle-$i",
        emptyList(),
        emptyList(),
        CordappManifest(
                "mock-bundle-symbolic",
                "$i",
                1,
                1,
                cordappType,
                "mock-shortname",
                "r3",
                i,
                "None",
                emptyMap()
                    ),
        CpkType.UNKNOWN,
        SecureHash(DigestAlgorithmName.DEFAULT_ALGORITHM_NAME.name, ByteArray(32) { i.toByte() }),
        emptySet(),
        Instant.now()
    )
}