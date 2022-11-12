package net.corda.ledger.utxo.flow.impl.transaction.serializer.tests

import net.corda.flow.pipeline.sandbox.FlowSandboxService
import net.corda.internal.serialization.AMQP_STORAGE_CONTEXT
import net.corda.internal.serialization.amqp.DeserializationInput
import net.corda.internal.serialization.amqp.ObjectAndEnvelope
import net.corda.internal.serialization.amqp.SerializationOutput
import net.corda.internal.serialization.amqp.SerializerFactory
import net.corda.internal.serialization.amqp.SerializerFactoryBuilder
import net.corda.internal.serialization.registerCustomSerializers
import net.corda.ledger.common.data.transaction.factory.WireTransactionFactory
import net.corda.ledger.utxo.flow.impl.transaction.factory.UtxoSignedTransactionFactory
import net.corda.ledger.utxo.testkit.createExample
import net.corda.sandbox.SandboxGroup
import net.corda.sandboxgroupcontext.getSandboxSingletonService
import net.corda.sandboxgroupcontext.getSandboxSingletonServices
import net.corda.serialization.InternalCustomSerializer
import net.corda.serialization.SerializationContext
import net.corda.testing.sandboxes.SandboxSetup
import net.corda.testing.sandboxes.fetchService
import net.corda.testing.sandboxes.lifecycle.AllTestsLifecycle
import net.corda.testing.sandboxes.testkit.VirtualNodeService
import net.corda.v5.application.marshalling.JsonMarshallingService
import net.corda.v5.ledger.utxo.transaction.UtxoSignedTransaction
import net.corda.v5.serialization.SerializedBytes
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions.assertDoesNotThrow
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS
import org.junit.jupiter.api.Timeout
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.RegisterExtension
import org.junit.jupiter.api.io.TempDir
import org.osgi.framework.BundleContext
import org.osgi.test.common.annotation.InjectBundleContext
import org.osgi.test.common.annotation.InjectService
import org.osgi.test.junit5.context.BundleContextExtension
import org.osgi.test.junit5.service.ServiceExtension
import java.io.NotSerializableException
import java.nio.file.Path
import java.util.concurrent.TimeUnit

private const val TESTING_CPB = "/META-INF/ledger-utxo-state-app.cpb"
private const val TIMEOUT_MILLIS = 10000L

@Timeout(value = 30, unit = TimeUnit.SECONDS)
@ExtendWith(ServiceExtension::class, BundleContextExtension::class)
@TestInstance(PER_CLASS)
class UtxoSignedTransactionAMQPSerializationTest {
    private val testSerializationContext = AMQP_STORAGE_CONTEXT

    @RegisterExtension
    private val lifecycle = AllTestsLifecycle()

    private lateinit var flowSandboxService: FlowSandboxService
    private lateinit var jsonMarshallingService: JsonMarshallingService
    private lateinit var wireTransactionFactory: WireTransactionFactory
    private lateinit var utxoSignedTransactionFactory: UtxoSignedTransactionFactory

    private lateinit var sandboxGroup: SandboxGroup
    private lateinit var internalCustomSerializers: Set<InternalCustomSerializer<out Any>>

    @BeforeAll
    fun setUp(
        @InjectService(timeout = 1000)
        sandboxSetup: SandboxSetup,
        @InjectBundleContext
        bundleContext: BundleContext,
        @TempDir
        baseDirectory: Path

    ) {
        sandboxSetup.configure(bundleContext, baseDirectory)
        lifecycle.accept(sandboxSetup) { setup ->
            flowSandboxService = setup.fetchService(TIMEOUT_MILLIS)

            val virtualNode = setup.fetchService<VirtualNodeService>(TIMEOUT_MILLIS)
            val virtualNodeInfo = virtualNode.loadVirtualNode(TESTING_CPB)
            val sandboxGroupContext = flowSandboxService.get(virtualNodeInfo.holdingIdentity)
            setup.withCleanup { virtualNode.unloadSandbox(sandboxGroupContext) }
            sandboxGroup = sandboxGroupContext.sandboxGroup

            jsonMarshallingService = sandboxGroupContext.getSandboxSingletonService()
            wireTransactionFactory = sandboxGroupContext.getSandboxSingletonService()
            utxoSignedTransactionFactory = sandboxGroupContext.getSandboxSingletonService()

            internalCustomSerializers = sandboxGroupContext.getSandboxSingletonServices()
        }
    }

    private fun testDefaultFactory(sandboxGroup: SandboxGroup): SerializerFactory =
        SerializerFactoryBuilder.build(sandboxGroup, allowEvolution = true).also {
            registerCustomSerializers(it)
            internalCustomSerializers.map{ customSerializer->
                it.register(customSerializer, it)
            }
        }

    @Throws(NotSerializableException::class)
    inline fun <reified T : Any> DeserializationInput.deserializeAndReturnEnvelope(
        bytes: SerializedBytes<T>,
        serializationContext: SerializationContext
    ): ObjectAndEnvelope<T> = deserializeAndReturnEnvelope(bytes, T::class.java, serializationContext)

    @Test
    @Suppress("FunctionName")
    fun `successfully serialize and deserialize a utxo Signed Transaction`() {

        // Initialised two serialisation factories to avoid having successful tests due to caching
        val factory1 = testDefaultFactory(sandboxGroup)
        val factory2 = testDefaultFactory(sandboxGroup)

        // Initialise the serialisation context
        val testSerializationContext = testSerializationContext.withSandboxGroup(sandboxGroup)

        val signedTransaction = utxoSignedTransactionFactory.createExample(
            jsonMarshallingService,
            wireTransactionFactory,
            utxoSignedTransactionFactory
        )
        val serialised = SerializationOutput(factory1).serialize(signedTransaction, testSerializationContext)


        // Perform deserialization and check if the correct class is deserialized
        val deserialized =
            DeserializationInput(factory2).deserializeAndReturnEnvelope(serialised, testSerializationContext)

        assertThat(deserialized.obj.javaClass.name)
            .isEqualTo("net.corda.ledger.utxo.flow.impl.transaction.UtxoSignedTransactionImpl")

        assertThat(deserialized.obj)
            .isInstanceOf(UtxoSignedTransaction::class.java)
            .isEqualTo(signedTransaction)

        assertDoesNotThrow {
            deserialized.obj.id
        }
        assertThat(deserialized.obj.id).isEqualTo(signedTransaction.id)
    }
}
