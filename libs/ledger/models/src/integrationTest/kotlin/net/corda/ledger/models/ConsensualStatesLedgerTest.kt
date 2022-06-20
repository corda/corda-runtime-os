package net.corda.ledger.models

import net.corda.testing.sandboxes.SandboxSetup
import net.corda.testing.sandboxes.fetchService
import net.corda.testing.sandboxes.lifecycle.EachTestLifecycle
import net.corda.sandboxgroupcontext.test.VirtualNodeService
import net.corda.v5.ledger.models.ConsensualStatesLedger
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.RegisterExtension
import org.junit.jupiter.api.io.TempDir
import org.osgi.framework.BundleContext
import org.osgi.test.common.annotation.InjectBundleContext
import org.osgi.test.common.annotation.InjectService
import org.osgi.test.junit5.context.BundleContextExtension
import org.osgi.test.junit5.service.ServiceExtension
import java.nio.file.Path

@ExtendWith(ServiceExtension::class, BundleContextExtension::class)
@TestInstance(PER_CLASS)
@Suppress("FunctionName")
class ConsensualStatesLedgerTest {
    companion object {
        private const val TIMEOUT_MILLIS = 1000L
        private const val CPB = "META-INF/consensual-ledger.cpb"
        private const val CPK_FLOWS_PACKAGE = "net.cordapp.demo.consensual"
        private const val CPK_BASIC_FLOW = "$CPK_FLOWS_PACKAGE.ConsensualFlow"
    }

    @RegisterExtension
    private val lifecycle = EachTestLifecycle()

    private lateinit var virtualNode: VirtualNodeService

    @InjectService(timeout = TIMEOUT_MILLIS)
    // lateinit var securityManagerService: SecurityManagerService
    lateinit var consensualStatesService: ConsensualStatesLedger

    @BeforeAll
    fun setup(
        @InjectService(timeout = 1000)
        sandboxSetup: SandboxSetup,
        @InjectBundleContext
        bundleContext: BundleContext,
        @TempDir
        testDirectory: Path
    ) {
        sandboxSetup.configure(bundleContext, testDirectory)
        lifecycle.accept(sandboxSetup) { setup ->
            virtualNode = setup.fetchService(timeout = 1000)
        }
    }

    @Suppress("unused")
    @BeforeEach
    fun reset() {
        // securityManagerService.startRestrictiveMode()
    }

    @Test
    fun `dummy flow runs`() {
        val sandboxGroupContext = virtualNode.loadSandbox(CPB)
        assertThat(
            virtualNode.runFlow<Map<String, String>>(CPK_BASIC_FLOW, sandboxGroupContext)
        ).isNotNull
    }
}
