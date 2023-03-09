@file:Suppress("deprecation")
package net.corda.sandboxgroupcontext.test

import net.corda.sandboxgroupcontext.SandboxGroupType
import net.corda.securitymanager.SecurityManagerService
import net.corda.testing.sandboxes.SandboxSetup
import net.corda.testing.sandboxes.fetchService
import net.corda.testing.sandboxes.lifecycle.EachTestLifecycle
import net.corda.testing.securitymanager.denyPermissions
import net.corda.testing.securitymanager.grantPermissions
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
import java.lang.reflect.ReflectPermission
import java.net.SocketPermission
import java.net.URLPermission
import java.nio.file.Path
import java.security.AccessControlException

@ExtendWith(ServiceExtension::class, BundleContextExtension::class)
@TestInstance(PER_CLASS)
@Suppress("FunctionName")
class SecurityManagerTests {
    companion object {
        private const val TIMEOUT_MILLIS = 10000L
        private const val CPB1 = "META-INF/sandbox-security-manager-one.cpb"
        private const val CPK1_FLOWS_PACKAGE = "com.example.securitymanager.one.flows"
        private const val CPK2_FLOWS_PACKAGE = "com.example.securitymanager.two.flows"
        private const val CPK1_ENVIRONMENT_FLOW = "$CPK1_FLOWS_PACKAGE.EnvironmentFlow"
        private const val CPK1_JSON_FLOW = "$CPK1_FLOWS_PACKAGE.JsonFlow"
        private const val CPK2_JSON_FLOW = "${CPK2_FLOWS_PACKAGE}.JsonFlow"
        private const val CPK1_HTTP_FLOW = "${CPK1_FLOWS_PACKAGE}.HttpRequestFlow"
        private const val CPK1_PRIVILEGED_JSON_FLOW = "${CPK1_FLOWS_PACKAGE}.PrivilegedJsonFlow"
        private const val CPK1_REFLECTION_FLOW = "$CPK1_FLOWS_PACKAGE.ReflectionJavaFlow"
        private const val CPK2_REFLECTION_FLOW = "$CPK2_FLOWS_PACKAGE.ReflectionJavaFlow"
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

    @Test
    fun `retrieving environment allowed by default`() {
        val sandboxGroupContext = virtualNode.loadSandbox(CPB1, SandboxGroupType.FLOW)
        assertThat(
            virtualNode.runFlow<Map<String, String>>(CPK1_ENVIRONMENT_FLOW, sandboxGroupContext)
        ).isNotNull
    }

    @Test
    fun `retrieving environment fails when permission denied`() {
        securityManagerService.denyPermissions("FLOW/*", listOf(
            RuntimePermission("getenv.*", null)
        ))

        val sandboxGroupContext = virtualNode.loadSandbox(CPB1, SandboxGroupType.FLOW)
        assertThrows<AccessControlException> {
            virtualNode.runFlow<Map<String, String>>(CPK1_ENVIRONMENT_FLOW, sandboxGroupContext)
        }
    }

    @Test
    fun `reflection allowed by default`() {
        val sandboxGroupContext = virtualNode.loadSandbox(CPB1, SandboxGroupType.FLOW)

        assertThat(
            virtualNode.runFlow<String>(CPK1_REFLECTION_FLOW, sandboxGroupContext)
        ).isEqualTo("test")
    }

    @Test
    fun `reflection fails when permission denied`() {
        securityManagerService.denyPermissions("FLOW/*", listOf(
            ReflectPermission("suppressAccessChecks")
        ))

        val sandboxGroupContext = virtualNode.loadSandbox(CPB1, SandboxGroupType.FLOW)

        assertThrows<AccessControlException> {
            virtualNode.runFlow<String>(CPK1_REFLECTION_FLOW, sandboxGroupContext)
        }
    }

    @Test
    fun `reflection allowed for one CPK but not for other`() {
        securityManagerService.denyPermissions("FLOW/*com.example.securitymanager.sandbox-security-manager-two*", listOf(
            ReflectPermission("suppressAccessChecks")
        ))

        val sandboxGroupContext1 = virtualNode.loadSandbox(CPB1, SandboxGroupType.FLOW)

        assertThat(
            virtualNode.runFlow<String>(CPK1_REFLECTION_FLOW, sandboxGroupContext1)
        ).isEqualTo("test")

        assertThrows<AccessControlException> {
            virtualNode.runFlow<String>(CPK2_REFLECTION_FLOW, sandboxGroupContext1)
        }
    }

    @Test
    fun `Reflection used by json deserialization allowed by default`() {
        val sandboxGroupContext = virtualNode.loadSandbox(CPB1, SandboxGroupType.FLOW)

        assertThat(
            virtualNode.runFlow<String>(CPK1_JSON_FLOW, sandboxGroupContext)
        ).isEqualTo("{\"value\":\"test\"}")
    }

    @Test
    fun `Reflection used by json fails when permission denied to CPK`() {
        val sandboxGroupContext = virtualNode.loadSandbox(CPB1, SandboxGroupType.FLOW)

        securityManagerService.denyPermissions("FLOW/*", listOf(
            ReflectPermission("suppressAccessChecks")
        ))

        val exception = assertThrows<Exception> {
            virtualNode.runFlow<String>(CPK1_JSON_FLOW, sandboxGroupContext)
        }
        assertThat(exception.message).contains(EXPECTED_ERROR_MSG)
    }

    @Test
    fun `Reflection used by json fails when permission denied to json library`() {
        val sandboxGroupContext = virtualNode.loadSandbox(CPB1, SandboxGroupType.FLOW)

        securityManagerService.denyPermissions("FLOW/*/privatelib/jackson*", listOf(
            ReflectPermission("suppressAccessChecks")
        ))

        val exception = assertThrows<Exception> {
            virtualNode.runFlow<String>(CPK1_JSON_FLOW, sandboxGroupContext)
        }
        assertThat(exception.message).contains(EXPECTED_ERROR_MSG)
    }

    @Test
    fun `Reflection allowed to CPK flow but not to json library`() {
        val sandboxGroupContext = virtualNode.loadSandbox(CPB1, SandboxGroupType.FLOW)

        securityManagerService.denyPermissions("FLOW/*/privatelib/jackson*", listOf(
            ReflectPermission("suppressAccessChecks")
        ))

        assertThat(
            virtualNode.runFlow<String>(CPK1_REFLECTION_FLOW, sandboxGroupContext)
        ).isEqualTo("test")

        val exception = assertThrows<Exception> {
            virtualNode.runFlow<String>(CPK1_JSON_FLOW, sandboxGroupContext)
        }
        assertThat(exception.message).contains(EXPECTED_ERROR_MSG)
    }

    @Test
    fun `Reflection fails when permission denied on call stack`() {
        val sandboxGroupContext = virtualNode.loadSandbox(CPB1, SandboxGroupType.FLOW)

        securityManagerService.denyPermissions("FLOW/*", listOf(
            ReflectPermission("suppressAccessChecks")
        ))

        securityManagerService.grantPermissions("FLOW/*/lib/jackson*", listOf(
            ReflectPermission("suppressAccessChecks")
        ))

        val exception = assertThrows<Exception> {
            virtualNode.runFlow<String>(CPK1_JSON_FLOW, sandboxGroupContext)
        }
        assertThat(exception.message).contains(EXPECTED_ERROR_MSG)
    }

    @Test
    fun `Reflection allowed via PrivilegedAction`() {
        val sandboxGroupContext = virtualNode.loadSandbox(CPB1, SandboxGroupType.FLOW)

        // Permission denied to CPK1
        securityManagerService.denyPermissions("FLOW/*com.example.securitymanager.sandbox-security-manager-one*", listOf(
            ReflectPermission("suppressAccessChecks")
        ))

        // CPK1 reflection fails
        val exception = assertThrows<Exception> {
            virtualNode.runFlow<String>(CPK1_JSON_FLOW, sandboxGroupContext)
        }
        assertThat(exception.message).contains(EXPECTED_ERROR_MSG)

        // CPK2 reflection works
        assertThat(
            virtualNode.runFlow<String>(CPK2_JSON_FLOW, sandboxGroupContext)
        ).isEqualTo("{\"value\":\"test\"}")

        // CPK1 reflection works via PrivilegedAction
        assertThat(
            virtualNode.runFlow<String>(CPK1_PRIVILEGED_JSON_FLOW, sandboxGroupContext)
        ).isEqualTo("{\"value\":\"test\"}")
    }

    @Test
    fun `HTTP connection allowed by default`() {
        val sandboxGroupContext = virtualNode.loadSandbox(CPB1, SandboxGroupType.FLOW)

        assertThat(
            virtualNode.runFlow<Int>(CPK1_HTTP_FLOW, sandboxGroupContext)
        ).isEqualTo(200)
    }

    @Test
    fun `HTTP connection fails when permission denied`() {
        val sandboxGroupContext = virtualNode.loadSandbox(CPB1, SandboxGroupType.FLOW)

        securityManagerService.denyPermissions("FLOW/*", listOf(
            URLPermission("http://*:*"),
            URLPermission("https://*:*"),
            SocketPermission("*:1-", "accept,listen,connect,resolve")
        ))

        assertThrows<AccessControlException> {
            virtualNode.runFlow<Int>(CPK1_HTTP_FLOW, sandboxGroupContext)
        }
    }
}