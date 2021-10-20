package net.corda.securitymanager.osgi

import org.junit.jupiter.api.Assertions.assertDoesNotThrow
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import org.osgi.framework.FrameworkUtil
import org.osgi.service.permissionadmin.PermissionInfo
import org.osgi.test.common.annotation.InjectService
import org.osgi.test.junit5.service.ServiceExtension
import java.security.AccessControlException

/** Tests the permissions of sandboxed bundles. */
@ExtendWith(ServiceExtension::class)
class SandboxPermissionTests {
    companion object {
        @InjectService(timeout = 1000)
        lateinit var sandboxLoader: SandboxLoader

        @Suppress("unused")
        @JvmStatic
        @BeforeAll
        fun setup() {
            sandboxLoader.securityManagerService.start()
        }
    }

    @Test
    fun `sandboxed bundle does not have general permissions`() {
        val sandboxedInvoker = sandboxLoader.getSandboxedInvoker()
        assertThrows<AccessControlException> {
            // A `RuntimePermission` is used here as a stand-in for testing all the various permissions and actions.
            sandboxedInvoker.performActionRequiringRuntimePermission()
        }
    }

    @Test
    fun `sandboxed bundle has service get permissions`() {
        val sandboxedInvoker = sandboxLoader.getSandboxedInvoker()
        assertDoesNotThrow {
            sandboxedInvoker.performActionRequiringServiceGetPermission()
        }
    }

    @Test
    fun `sandboxed bundle has service register permissions`() {
        val sandboxedInvoker = sandboxLoader.getSandboxedInvoker()
        assertDoesNotThrow {
            sandboxedInvoker.performActionRequiringServiceRegisterPermission()
        }
    }

    @Test
    fun `sandboxed bundle can be granted additional permissions`() {
        val sandboxedInvoker = sandboxLoader.getSandboxedInvoker()
        val filter = FrameworkUtil.getBundle(sandboxedInvoker::class.java).location

        // TODO: Use constant.
        val perm = PermissionInfo(RuntimePermission::class.java.name, "getenv.ENV_VAR", null)

        sandboxLoader.securityManagerService.grantPermission(filter, listOf(perm))

        assertDoesNotThrow {
            sandboxedInvoker.performActionRequiringRuntimePermission()
        }
    }

    // TODO: Test of filter (and not just whole location).
}