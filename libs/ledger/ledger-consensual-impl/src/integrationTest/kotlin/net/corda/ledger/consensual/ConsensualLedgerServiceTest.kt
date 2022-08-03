package net.corda.ledger.consensual

import net.corda.flow.fiber.FlowFiber
import net.corda.flow.fiber.FlowFiberExecutionContext
import net.corda.flow.fiber.FlowFiberService
import net.corda.flow.pipeline.sandbox.FlowSandboxGroupContext
import net.corda.membership.read.MembershipGroupReader
import net.corda.v5.base.types.MemberX500Name
import net.corda.v5.cipher.suite.CipherSchemeMetadata
import net.corda.v5.ledger.consensual.ConsensualLedgerService
import net.corda.v5.ledger.consensual.transaction.ConsensualTransactionBuilder
import net.corda.virtualnode.HoldingIdentity
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS
import org.osgi.test.common.annotation.InjectService

import net.corda.crypto.impl.serialization.PublicKeySerializer
import net.corda.flow.state.FlowCheckpoint
import net.corda.internal.serialization.SerializationContextImpl
import net.corda.internal.serialization.SerializationServiceImpl
import net.corda.internal.serialization.amqp.DefaultDescriptorBasedSerializerRegistry
import net.corda.internal.serialization.amqp.DescriptorBasedSerializerRegistry
import net.corda.internal.serialization.amqp.DeserializationInput
import net.corda.internal.serialization.amqp.SerializationOutput
import net.corda.internal.serialization.amqp.SerializerFactory
import net.corda.internal.serialization.amqp.SerializerFactoryBuilder
import net.corda.internal.serialization.amqp.amqpMagic
import net.corda.internal.serialization.amqp.currentSandboxGroup
import net.corda.internal.serialization.registerCustomSerializers
import net.corda.libs.packaging.core.CpkMetadata
import net.corda.sandbox.SandboxGroup
import net.corda.serialization.SerializationContext
import net.corda.v5.application.serialization.SerializationService
import net.corda.v5.cipher.suite.KeyEncodingService
import net.corda.v5.serialization.SingletonSerializeAsToken
import org.mockito.Mockito
import org.mockito.Mockito.mock
import org.osgi.framework.Bundle
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference

//TODO(Deduplicate with net.corda.internal.serialization.amqp.testutils)

private class MockSandboxGroup(private val classLoader: ClassLoader = ClassLoader.getSystemClassLoader()) :
    SandboxGroup {
    override val metadata: Map<Bundle, CpkMetadata> = emptyMap()

    override fun loadClassFromMainBundles(className: String): Class<*> =
        Class.forName(className, false, classLoader)
    override fun <T : Any> loadClassFromMainBundles(className: String, type: Class<T>): Class<out T> =
        Class.forName(className, false, classLoader).asSubclass(type)
    override fun getClass(className: String, serialisedClassTag: String): Class<*> = Class.forName(className)
    override fun getStaticTag(klass: Class<*>): String = "S;bundle;sandbox"
    override fun getEvolvableTag(klass: Class<*>) = "E;bundle;sandbox"
}

private val testSerializationContext = SerializationContextImpl(
    preferredSerializationVersion = amqpMagic,
    properties = mutableMapOf(),
    objectReferencesEnabled = false,
    useCase = SerializationContext.UseCase.Testing,
    encoding = null,
    sandboxGroup = MockSandboxGroup()
)

private fun testDefaultFactoryNoEvolution(
    keyEncodingService: KeyEncodingService,
    descriptorBasedSerializerRegistry: DescriptorBasedSerializerRegistry =
        DefaultDescriptorBasedSerializerRegistry()
): SerializerFactory =
    SerializerFactoryBuilder.build(
        testSerializationContext.currentSandboxGroup(),
        descriptorBasedSerializerRegistry = descriptorBasedSerializerRegistry,
        allowEvolution = false
    ).also {
        registerCustomSerializers(it)
        it.register(PublicKeySerializer(keyEncodingService), it)
    }

class TestSerializationService {
    companion object{
        fun getTestSerializationService(keyEncodingService: KeyEncodingService) : SerializationService {
            val factory = testDefaultFactoryNoEvolution(keyEncodingService)
            val output = SerializationOutput(factory)
            val input = DeserializationInput(factory)
            val context = testSerializationContext

            return SerializationServiceImpl(output, input, context)
        }
    }
}

@Component(service = [FlowFiberService::class, SingletonSerializeAsToken::class])
class MockFlowFiberService @Activate constructor(
    @Reference(service = CipherSchemeMetadata::class) private val schemeMetadata: CipherSchemeMetadata
) : FlowFiberService, SingletonSerializeAsToken {
    private val mockFlowFiber: FlowFiber

    init{
        val serializer = TestSerializationService.getTestSerializationService(schemeMetadata)
        val mockFlowSandboxGroupContext = mock(FlowSandboxGroupContext::class.java)
        Mockito.`when`(mockFlowSandboxGroupContext.amqpSerializer).thenReturn(serializer)

        val membershipGroupReader: MembershipGroupReader = mock(MembershipGroupReader::class.java)
        val BOB_X500 = "CN=Bob, O=Bob Corp, L=LDN, C=GB"
        val BOB_X500_NAME = MemberX500Name.parse(BOB_X500)
        val holdingIdentity =  HoldingIdentity(BOB_X500_NAME,"group1")
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

}

@TestInstance(PER_CLASS)
@Suppress("FunctionName")
class ConsensualLedgerServiceTest {

    @InjectService(timeout = 1000)
    lateinit var consensualLedgerService: ConsensualLedgerService

    @Test
    fun `getTransactionBuilder should return a Transaction Builder`() {
        val transactionBuilder = consensualLedgerService.getTransactionBuilder()
        assertThat(transactionBuilder).isInstanceOf(ConsensualTransactionBuilder::class.java)
    }
}
