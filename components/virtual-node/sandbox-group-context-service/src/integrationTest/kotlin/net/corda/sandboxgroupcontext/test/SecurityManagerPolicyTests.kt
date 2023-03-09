@file:Suppress("deprecation")
package net.corda.sandboxgroupcontext.test

import net.corda.sandboxgroupcontext.SandboxGroupType
import net.corda.securitymanager.SecurityManagerService
import net.corda.testing.sandboxes.SandboxSetup
import net.corda.testing.sandboxes.fetchService
import net.corda.testing.sandboxes.lifecycle.EachTestLifecycle
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS
import org.junit.jupiter.api.Timeout
import org.junit.jupiter.api.assertThrows
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
import java.security.AccessControlException
import java.util.concurrent.TimeUnit.SECONDS

@ExtendWith(ServiceExtension::class, BundleContextExtension::class)
@TestInstance(PER_CLASS)
@Suppress("FunctionName")
class SecurityManagerPolicyTests {
    companion object {
        private const val TIMEOUT_MILLIS = 10000L
        private const val CPB1 = "META-INF/sandbox-security-manager-one.cpb"
        private const val CPK1_FLOWS_PACKAGE = "com.example.securitymanager.one.flows"
        private const val CPK1_ENVIRONMENT_FLOW = "$CPK1_FLOWS_PACKAGE.EnvironmentFlow"
        private const val CPK1_FLOW_ENGINE_FLOW = "$CPK1_FLOWS_PACKAGE.FlowEngineFlow"
        private const val CPK1_JSON_FLOW = "$CPK1_FLOWS_PACKAGE.JsonFlow"
        private const val CPK1_HTTP_FLOW = "${CPK1_FLOWS_PACKAGE}.HttpRequestFlow"
        private const val CPK1_REFLECTION_FLOW = "$CPK1_FLOWS_PACKAGE.ReflectionJavaFlow"
        private const val EXPECTED_ERROR_MSG = "access denied (\"java.lang.reflect.ReflectPermission\" \"suppressAccessChecks\")"
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

    private fun applyPolicyFile(fileName: String) {
        val url = this::class.java.classLoader.getResource(fileName) ?: fail("Resource $fileName not found")
        val policy = securityManagerService.readPolicy(url.openConnection().getInputStream())
        securityManagerService.updatePermissions(policy, clear = false)
    }

    @Test
    fun `retrieving environment fails when permission denied`() {
        applyPolicyFile("security_01.policy")
        val sandboxGroupContext = virtualNode.loadSandbox(CPB1, SandboxGroupType.FLOW)

        assertThrows<AccessControlException> {
            virtualNode.runFlow<Map<String, String>>(CPK1_ENVIRONMENT_FLOW, sandboxGroupContext)
        }
    }

    @Test
    fun `reflection fails when permission denied`() {
        applyPolicyFile("security_02.policy")
        val sandboxGroupContext = virtualNode.loadSandbox(CPB1, SandboxGroupType.FLOW)

        assertThrows<AccessControlException> {
            virtualNode.runFlow<String>(CPK1_REFLECTION_FLOW, sandboxGroupContext)
        }
    }

    @Test
    fun `HTTP connection fails when permission denied`() {
        applyPolicyFile("security_03.policy")
        val sandboxGroupContext = virtualNode.loadSandbox(CPB1, SandboxGroupType.FLOW)

        assertThrows<AccessControlException> {
            virtualNode.runFlow<Int>(CPK1_HTTP_FLOW, sandboxGroupContext)
        }
    }

    @Test
    fun `Reflection fails when permission denied on call stack`() {
        applyPolicyFile("security_04.policy")
        val sandboxGroupContext = virtualNode.loadSandbox(CPB1, SandboxGroupType.FLOW)

        val exception = assertThrows<Exception> {
            virtualNode.runFlow<String>(CPK1_JSON_FLOW, sandboxGroupContext)
        }
        assertThat(exception.message).contains(EXPECTED_ERROR_MSG)
    }

    @Test
    fun `policy can be changed in runtime`() {
        applyPolicyFile("security_02.policy")
        val sandboxGroupContext = virtualNode.loadSandbox(CPB1, SandboxGroupType.FLOW)

        assertThrows<AccessControlException> {
            virtualNode.runFlow<String>(CPK1_REFLECTION_FLOW, sandboxGroupContext)
        }

        applyPolicyFile("security_05.policy")

        assertThat(
            virtualNode.runFlow<String>(CPK1_REFLECTION_FLOW, sandboxGroupContext)
        ).isEqualTo("test")
    }

    @Timeout(30, unit = SECONDS)
    @Test
    fun `policy can forbid access to injectable service`() {
        val context1 = mutableListOf(virtualNode.loadSandbox(CPB1, SandboxGroupType.FLOW))
        try {
            val ex = assertThrows<UnsupportedOperationException> {
                virtualNode.runFlow<String>(CPK1_FLOW_ENGINE_FLOW, context1.single())
            }
            assertThat(ex).hasMessage("VICTORY IS MINE!")
        } finally {
            val completion = virtualNode.releaseSandbox(context1.single()) ?: fail("Sandbox1 is missing")
            context1.clear()
            virtualNode.unloadSandbox(completion)
        }

        // Update the security policy to deny sandboxes access to FlowEngine.
        applyPolicyFile("security-deny-flow-engine.policy")

        val context2 = mutableListOf(virtualNode.loadSandbox(CPB1, SandboxGroupType.FLOW))
        try {
            assertThat(
                virtualNode.runFlow<String>(CPK1_FLOW_ENGINE_FLOW, context2.single())
            ).isEqualTo("FlowEngine not found")
        } finally {
            val completion = virtualNode.releaseSandbox(context2.single()) ?: fail("Sandbox2 is missing")
            context2.clear()
            virtualNode.unloadSandbox(completion)
        }
    }
}
