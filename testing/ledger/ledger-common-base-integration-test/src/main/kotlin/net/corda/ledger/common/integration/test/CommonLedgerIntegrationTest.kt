package net.corda.ledger.common.integration.test

import net.corda.flow.pipeline.sandbox.FlowSandboxService
import net.corda.flow.pipeline.sandbox.impl.FlowSandboxGroupContextImpl
import net.corda.internal.serialization.AMQP_STORAGE_CONTEXT
import net.corda.ledger.common.data.transaction.WireTransaction
import net.corda.ledger.common.data.transaction.factory.WireTransactionFactory
import net.corda.ledger.common.testkit.createExample
import net.corda.sandboxgroupcontext.RequireSandboxAMQP
import net.corda.sandboxgroupcontext.SandboxGroupContext
import net.corda.sandboxgroupcontext.getObjectByKey
import net.corda.sandboxgroupcontext.getSandboxSingletonService
import net.corda.serialization.checkpoint.CheckpointSerializer
import net.corda.testing.sandboxes.SandboxSetup
import net.corda.testing.sandboxes.fetchService
import net.corda.testing.sandboxes.lifecycle.AllTestsLifecycle
import net.corda.testing.sandboxes.testkit.VirtualNodeService
import net.corda.v5.application.marshalling.JsonMarshallingService
import net.corda.v5.application.serialization.SerializationService
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
    lateinit var sandboxGroupContext1: SandboxGroupContext
    lateinit var jsonMarshallingService: JsonMarshallingService
    lateinit var sandboxSerializationService1: SerializationService
    lateinit var sandboxSerializationService2: SerializationService
    lateinit var wireTransactionFactory: WireTransactionFactory
    lateinit var wireTransaction: WireTransaction
    lateinit var kryoSerializer: CheckpointSerializer

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

    open fun initialize(setup: SandboxSetup) {
        flowSandboxService = setup.fetchService(TIMEOUT_MILLIS)

        val virtualNode1 = setup.fetchService<VirtualNodeService>(TIMEOUT_MILLIS)
        val virtualNodeInfo1 = virtualNode1.loadVirtualNode(testingCpb)
        sandboxGroupContext1 = flowSandboxService.get(virtualNodeInfo1.holdingIdentity)
        setup.withCleanup { virtualNode1.unloadSandbox(sandboxGroupContext1) }

        val virtualNode2 = setup.fetchService<VirtualNodeService>(TIMEOUT_MILLIS)
        val virtualNodeInfo2 = virtualNode2.loadVirtualNode(testingCpb)
        val sandboxGroupContext2 = flowSandboxService.get(virtualNodeInfo2.holdingIdentity)
        setup.withCleanup { virtualNode2.unloadSandbox(sandboxGroupContext2) }

        jsonMarshallingService = sandboxGroupContext1.getSandboxSingletonService()
        wireTransactionFactory = sandboxGroupContext1.getSandboxSingletonService()
        kryoSerializer = sandboxGroupContext1.getObjectByKey(FlowSandboxGroupContextImpl.CHECKPOINT_SERIALIZER)
            ?: fail("No CheckpointSerializer in sandbox context")
        sandboxSerializationService1 = sandboxGroupContext1.getObjectByKey(RequireSandboxAMQP.AMQP_SERIALIZATION_SERVICE)
            ?: fail("No Serializer in sandbox context")
        sandboxSerializationService2 = sandboxGroupContext2.getObjectByKey(RequireSandboxAMQP.AMQP_SERIALIZATION_SERVICE)
            ?: fail("No Serializer in sandbox context")

        wireTransaction = wireTransactionFactory.createExample(jsonMarshallingService)
    }
}