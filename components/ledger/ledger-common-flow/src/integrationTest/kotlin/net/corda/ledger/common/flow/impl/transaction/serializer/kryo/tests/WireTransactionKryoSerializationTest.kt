package net.corda.ledger.common.flow.impl.transaction.serializer.kryo.tests

import net.corda.flow.pipeline.sandbox.FlowSandboxService
import net.corda.flow.pipeline.sandbox.impl.FlowSandboxGroupContextImpl.Companion.CHECKPOINT_SERIALIZER
import net.corda.ledger.common.data.transaction.WireTransaction
import net.corda.ledger.common.data.transaction.factory.WireTransactionFactory
import net.corda.ledger.common.testkit.createExample
import net.corda.sandboxgroupcontext.getObjectByKey
import net.corda.sandboxgroupcontext.getSandboxSingletonService
import net.corda.serialization.checkpoint.CheckpointSerializer
import net.corda.testing.sandboxes.SandboxSetup
import net.corda.testing.sandboxes.fetchService
import net.corda.testing.sandboxes.lifecycle.AllTestsLifecycle
import net.corda.testing.sandboxes.testkit.VirtualNodeService
import net.corda.v5.application.marshalling.JsonMarshallingService
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions.assertDoesNotThrow
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS
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

private const val TESTING_CPB = "/META-INF/calculator.cpb"
private const val TIMEOUT_MILLIS = 10000L

@ExtendWith(ServiceExtension::class, BundleContextExtension::class)
@TestInstance(PER_CLASS)
class WireTransactionKryoSerializationTest {
    @RegisterExtension
    private val lifecycle = AllTestsLifecycle()

    private lateinit var flowSandboxService: FlowSandboxService
    private lateinit var jsonMarshallingService: JsonMarshallingService
    private lateinit var wireTransactionFactory: WireTransactionFactory
    private lateinit var kryoSerializer: CheckpointSerializer

    @BeforeAll
    fun setup(
        @InjectService(timeout = TIMEOUT_MILLIS)
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

            wireTransactionFactory = sandboxGroupContext.getSandboxSingletonService()
            jsonMarshallingService = sandboxGroupContext.getSandboxSingletonService()
            kryoSerializer = sandboxGroupContext.getObjectByKey(CHECKPOINT_SERIALIZER)
                ?: fail("No CheckpointSerializer in sandbox context")
        }
    }

    @Test
    @Suppress("FunctionName")
    fun `correct serialization of a wire Transaction`() {
        val wireTransaction = wireTransactionFactory.createExample(jsonMarshallingService)
        val bytes = kryoSerializer.serialize(wireTransaction)
        val deserialized = kryoSerializer.deserialize(bytes, WireTransaction::class.java)

        assertThat(deserialized).isEqualTo(wireTransaction)
        assertDoesNotThrow {
            deserialized.id
        }
        assertEquals(wireTransaction.id, deserialized.id)
    }
}
