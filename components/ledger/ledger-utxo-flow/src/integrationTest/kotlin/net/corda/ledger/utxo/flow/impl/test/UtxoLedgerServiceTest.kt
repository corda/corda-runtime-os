package net.corda.ledger.utxo.flow.impl.test

import net.corda.flow.pipeline.sandbox.FlowSandboxService
import net.corda.sandboxgroupcontext.getSandboxSingletonService
import net.corda.testing.sandboxes.SandboxSetup
import net.corda.testing.sandboxes.fetchService
import net.corda.testing.sandboxes.lifecycle.AllTestsLifecycle
import net.corda.testing.sandboxes.testkit.VirtualNodeService
import net.corda.v5.ledger.utxo.UtxoLedgerService
import net.corda.v5.ledger.utxo.transaction.UtxoTransactionBuilder
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.RegisterExtension
import org.junit.jupiter.api.io.TempDir
import org.osgi.framework.BundleContext
import org.osgi.test.common.annotation.InjectBundleContext
import org.osgi.test.common.annotation.InjectService
import org.osgi.test.junit5.context.BundleContextExtension
import org.osgi.test.junit5.service.ServiceExtension
import java.nio.file.Path

private const val TESTING_CPB = "/META-INF/ledger-utxo-state-app.cpb"
private const val TIMEOUT_MILLIS = 10000L

@ExtendWith(ServiceExtension::class, BundleContextExtension::class)
@TestInstance(PER_CLASS)
@Suppress("FunctionName")
class UtxoLedgerServiceTest {
    @RegisterExtension
    private val lifecycle = AllTestsLifecycle()

    private lateinit var flowSandboxService: FlowSandboxService
    private lateinit var utxoLedgerService: UtxoLedgerService

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
        lifecycle.accept(sandboxSetup) { setup ->
            flowSandboxService = setup.fetchService(TIMEOUT_MILLIS)

            val virtualNode = setup.fetchService<VirtualNodeService>(TIMEOUT_MILLIS)
            val virtualNodeInfo = virtualNode.loadVirtualNode(TESTING_CPB)
            val sandboxGroupContext = flowSandboxService.get(virtualNodeInfo.holdingIdentity)
            setup.withCleanup { virtualNode.unloadSandbox(sandboxGroupContext) }

            utxoLedgerService = sandboxGroupContext.getSandboxSingletonService()
        }
    }

    @Test
    fun `getTransactionBuilder should return a Transaction Builder`() {
        val transactionBuilder = utxoLedgerService.getTransactionBuilder()
        assertThat(transactionBuilder).isInstanceOf(UtxoTransactionBuilder::class.java)
    }
}
