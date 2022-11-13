package net.corda.ledger.consensual.testkit

import net.corda.flow.pipeline.sandbox.FlowSandboxService
import net.corda.flow.pipeline.sandbox.impl.FlowSandboxGroupContextImpl
import net.corda.internal.serialization.AMQP_STORAGE_CONTEXT
import net.corda.internal.serialization.amqp.DeserializationInput
import net.corda.internal.serialization.amqp.ObjectAndEnvelope
import net.corda.internal.serialization.amqp.SerializerFactory
import net.corda.internal.serialization.amqp.SerializerFactoryBuilder
import net.corda.internal.serialization.registerCustomSerializers
import net.corda.ledger.common.data.transaction.factory.WireTransactionFactory
import net.corda.ledger.consensual.flow.impl.transaction.factory.ConsensualSignedTransactionFactory
import net.corda.sandbox.SandboxGroup
import net.corda.sandboxgroupcontext.getObjectByKey
import net.corda.sandboxgroupcontext.getSandboxSingletonService
import net.corda.sandboxgroupcontext.getSandboxSingletonServices
import net.corda.serialization.InternalCustomSerializer
import net.corda.serialization.SerializationContext
import net.corda.serialization.checkpoint.CheckpointSerializer
import net.corda.testing.sandboxes.SandboxSetup
import net.corda.testing.sandboxes.fetchService
import net.corda.testing.sandboxes.lifecycle.AllTestsLifecycle
import net.corda.testing.sandboxes.testkit.VirtualNodeService
import net.corda.v5.application.marshalling.JsonMarshallingService
import net.corda.v5.ledger.consensual.ConsensualLedgerService
import net.corda.v5.ledger.consensual.transaction.ConsensualSignedTransaction
import net.corda.v5.serialization.SerializedBytes
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.RegisterExtension
import org.junit.jupiter.api.fail
import org.junit.jupiter.api.io.TempDir
import org.osgi.framework.BundleContext
import org.osgi.test.common.annotation.InjectBundleContext
import org.osgi.test.common.annotation.InjectService
import org.osgi.test.junit5.context.BundleContextExtension
import org.osgi.test.junit5.service.ServiceExtension
import java.io.NotSerializableException
import java.nio.file.Path

private const val TESTING_CPB = "/META-INF/ledger-consensual-state-app.cpb"
private const val TIMEOUT_MILLIS = 10000L

@ExtendWith(ServiceExtension::class, BundleContextExtension::class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
abstract class ConsensualLedgerIntegrationTest {
    @RegisterExtension
    val lifecycle = AllTestsLifecycle()

    val testSerializationContext = AMQP_STORAGE_CONTEXT

    lateinit var flowSandboxService: FlowSandboxService
    lateinit var jsonMarshallingService: JsonMarshallingService
    lateinit var wireTransactionFactory: WireTransactionFactory
    lateinit var kryoSerializer: CheckpointSerializer
    lateinit var sandboxGroup: SandboxGroup
    lateinit var internalCustomSerializers: Set<InternalCustomSerializer<out Any>>

    lateinit var consensualSignedTransactionFactory: ConsensualSignedTransactionFactory
    lateinit var consensualLedgerService: ConsensualLedgerService
    lateinit var consensualSignedTransaction: ConsensualSignedTransaction

    @BeforeAll
    fun setup(
        @InjectService(timeout = 1000)
        sandboxSetup: SandboxSetup,
        @InjectBundleContext
        bundleContext: BundleContext,
        @TempDir
        baseDirectory: Path
    ) {
        sandboxSetup.configure(bundleContext, baseDirectory)
        lifecycle.accept(sandboxSetup) { initialize(it) }
    }

    fun initialize(setup: SandboxSetup){
        flowSandboxService = setup.fetchService(TIMEOUT_MILLIS)

        val virtualNode = setup.fetchService<VirtualNodeService>(TIMEOUT_MILLIS)
        val virtualNodeInfo = virtualNode.loadVirtualNode(TESTING_CPB)
        val sandboxGroupContext = flowSandboxService.get(virtualNodeInfo.holdingIdentity)
        setup.withCleanup { virtualNode.unloadSandbox(sandboxGroupContext) }
        sandboxGroup = sandboxGroupContext.sandboxGroup

        jsonMarshallingService = sandboxGroupContext.getSandboxSingletonService()
        wireTransactionFactory = sandboxGroupContext.getSandboxSingletonService()
        kryoSerializer = sandboxGroupContext.getObjectByKey(FlowSandboxGroupContextImpl.CHECKPOINT_SERIALIZER)
            ?: fail("No CheckpointSerializer in sandbox context")

        internalCustomSerializers = sandboxGroupContext.getSandboxSingletonServices()

        consensualSignedTransactionFactory = sandboxGroupContext.getSandboxSingletonService()
        consensualLedgerService = sandboxGroupContext.getSandboxSingletonService()
        consensualSignedTransaction = consensualSignedTransactionFactory.createExample(
            jsonMarshallingService,
            wireTransactionFactory,
            consensualSignedTransactionFactory
        )
    }

    fun testDefaultFactory(sandboxGroup: SandboxGroup): SerializerFactory =
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
}