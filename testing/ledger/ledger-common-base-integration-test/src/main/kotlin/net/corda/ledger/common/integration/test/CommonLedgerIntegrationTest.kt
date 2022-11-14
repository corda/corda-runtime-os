package net.corda.ledger.common.integration.test

import net.corda.flow.pipeline.sandbox.FlowSandboxService
import net.corda.flow.pipeline.sandbox.impl.FlowSandboxGroupContextImpl
import net.corda.internal.serialization.AMQP_STORAGE_CONTEXT
import net.corda.internal.serialization.amqp.DeserializationInput
import net.corda.internal.serialization.amqp.ObjectAndEnvelope
import net.corda.internal.serialization.amqp.SerializerFactory
import net.corda.internal.serialization.amqp.SerializerFactoryBuilder
import net.corda.internal.serialization.registerCustomSerializers
import net.corda.ledger.common.data.transaction.WireTransaction
import net.corda.ledger.common.data.transaction.factory.WireTransactionFactory
import net.corda.ledger.common.testkit.createExample
import net.corda.sandbox.SandboxGroup
import net.corda.sandboxgroupcontext.SandboxGroupContext
import net.corda.sandboxgroupcontext.getObjectByKey
import net.corda.sandboxgroupcontext.getSandboxSingletonService
import net.corda.sandboxgroupcontext.getSandboxSingletonServices
import net.corda.serialization.InternalCustomSerializer
import net.corda.serialization.SerializationContext
import net.corda.serialization.checkpoint.CheckpointSerializer
import net.corda.testing.sandboxes.SandboxSetup
import net.corda.testing.sandboxes.fetchService
import net.corda.testing.sandboxes.lifecycle.AllTestsLifecycle
import net.corda.v5.application.marshalling.JsonMarshallingService
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

const val TIMEOUT_MILLIS = 10000L

@ExtendWith(ServiceExtension::class, BundleContextExtension::class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
abstract class CommonLedgerIntegrationTest {
    @RegisterExtension
    val lifecycle = AllTestsLifecycle()

    open val testingCpb = "/META-INF/ledger-common-empty-app.cpb"

    val testSerializationContext = AMQP_STORAGE_CONTEXT

    lateinit var flowSandboxService: FlowSandboxService
    lateinit var sandboxGroupContext: SandboxGroupContext
    lateinit var jsonMarshallingService: JsonMarshallingService
    lateinit var wireTransactionFactory: WireTransactionFactory
    lateinit var wireTransaction: WireTransaction
    lateinit var kryoSerializer: CheckpointSerializer
    lateinit var sandboxGroup: SandboxGroup
    lateinit var internalCustomSerializers: Set<InternalCustomSerializer<out Any>>

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

    open fun initialize(setup: SandboxSetup){
        flowSandboxService = setup.fetchService(TIMEOUT_MILLIS)

        val virtualNode = setup.fetchService<net.corda.testing.sandboxes.testkit.VirtualNodeService>(TIMEOUT_MILLIS)
        val virtualNodeInfo = virtualNode.loadVirtualNode(testingCpb)
        sandboxGroupContext = flowSandboxService.get(virtualNodeInfo.holdingIdentity)
        setup.withCleanup { virtualNode.unloadSandbox(sandboxGroupContext) }
        sandboxGroup = sandboxGroupContext.sandboxGroup

        jsonMarshallingService = sandboxGroupContext.getSandboxSingletonService()
        wireTransactionFactory = sandboxGroupContext.getSandboxSingletonService()
        kryoSerializer = sandboxGroupContext.getObjectByKey(FlowSandboxGroupContextImpl.CHECKPOINT_SERIALIZER)
            ?: fail("No CheckpointSerializer in sandbox context")

        internalCustomSerializers = sandboxGroupContext.getSandboxSingletonServices()

        wireTransaction = wireTransactionFactory.createExample(jsonMarshallingService)
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