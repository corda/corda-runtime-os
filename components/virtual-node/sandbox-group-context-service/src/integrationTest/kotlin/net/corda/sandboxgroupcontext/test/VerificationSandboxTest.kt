package net.corda.sandboxgroupcontext.test

import net.corda.sandboxgroupcontext.SandboxGroupType
import net.corda.securitymanager.SecurityManagerService
import net.corda.testing.sandboxes.SandboxSetup
import net.corda.testing.sandboxes.fetchService
import net.corda.testing.sandboxes.lifecycle.EachTestLifecycle
import net.corda.testing.securitymanager.denyPermissions
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS
import org.junit.jupiter.api.assertDoesNotThrow
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
import java.security.AccessControlException

@ExtendWith(ServiceExtension::class, BundleContextExtension::class)
@TestInstance(PER_CLASS)
@Suppress("FunctionName")
class VerificationSandboxTest {
    companion object {
        private const val TIMEOUT_MILLIS = 10000L
        private const val CPB1 = "META-INF/sandbox-security-manager-one.cpb"
        private const val CPK1_FLOWS_PACKAGE = "com.example.securitymanager.one.flows"
        private const val CPK1_ENVIRONMENT_FLOW = "$CPK1_FLOWS_PACKAGE.EnvironmentFlow"
    }

    @RegisterExtension
    private val lifecycle = EachTestLifecycle()

    private lateinit var virtualNode: VirtualNodeService

    @InjectService(timeout = TIMEOUT_MILLIS)
    lateinit var securityManagerService: SecurityManagerService

    @BeforeAll
    fun setup(
        @InjectService(timeout = TIMEOUT_MILLIS)
        sandboxSetup: SandboxSetup,
        @InjectBundleContext
        bundleContext: BundleContext,
        @TempDir
        testDirectory: Path
    ) {
        sandboxSetup.configure(bundleContext, testDirectory)
        lifecycle.accept(sandboxSetup) { setup ->
            virtualNode = setup.fetchService(TIMEOUT_MILLIS)
        }
    }

    @Suppress("unused")
    @BeforeEach
    fun reset() {
        securityManagerService.startRestrictiveMode()
    }

    @Test
    fun `retrieving environment allowed by default`() {
        val sandboxGroupContext = virtualNode.loadSandbox(CPB1, SandboxGroupType.VERIFICATION)
        assertThat(
            virtualNode.runFlow<Map<String, String>>(CPK1_ENVIRONMENT_FLOW, sandboxGroupContext)
        ).isNotNull
    }

    @Test
    fun `retrieving environment fails when permission denied`() {
        securityManagerService.denyPermissions("VERIFICATION/*", listOf(
            RuntimePermission("getenv.*", null)
        ))

        val sandboxGroupContext = virtualNode.loadSandbox(CPB1, SandboxGroupType.VERIFICATION)
        assertThrows<AccessControlException> {
            virtualNode.runFlow<Map<String, String>>(CPK1_ENVIRONMENT_FLOW, sandboxGroupContext)
        }
    }

    @Test
    fun `retrieving environment allowed for one sandbox type but not for another`() {
        securityManagerService.denyPermissions("VERIFICATION/*", listOf(
            RuntimePermission("getenv.*", null)
        ))

        val sandboxGroupContext1 = virtualNode.loadSandbox(CPB1, SandboxGroupType.FLOW)
        assertDoesNotThrow {
            virtualNode.runFlow<Map<String, String>>(CPK1_ENVIRONMENT_FLOW, sandboxGroupContext1)
        }

        val sandboxGroupContext2 = virtualNode.loadSandbox(CPB1, SandboxGroupType.VERIFICATION)
        assertThrows<AccessControlException> {
            virtualNode.runFlow<Map<String, String>>(CPK1_ENVIRONMENT_FLOW, sandboxGroupContext2)
        }
    }
}