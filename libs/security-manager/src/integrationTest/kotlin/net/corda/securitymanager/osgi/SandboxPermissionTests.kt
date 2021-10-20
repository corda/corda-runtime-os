package net.corda.securitymanager.osgi

import org.junit.jupiter.api.Assertions.assertDoesNotThrow
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import org.osgi.framework.FrameworkUtil
import org.osgi.test.common.annotation.InjectService
import org.osgi.test.junit5.service.ServiceExtension
import java.security.AccessControlException

/** Tests the permissions of sandboxed bundles. */
@ExtendWith(ServiceExtension::class)
class SandboxPermissionTests {
    companion object {
        private const val WILDCARD_MATCH = "*"
        private const val GET_ENV_TARGET = "getenv.$WILDCARD_MATCH"
        private const val GET_PROTECTION_DOMAIN_TARGET = "getProtectionDomain"

        // The permission to get any environment variable.
        private val getEnvPerm = RuntimePermission(GET_ENV_TARGET, null)

        // The permission to get a class's protection domain.
        private val getProtectionDomainPerm = RuntimePermission(GET_PROTECTION_DOMAIN_TARGET, null)

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
            sandboxedInvoker.performActionRequiringGetEnvRuntimePermission()
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
    fun `sandboxed bundle can be granted additional permissions using its full location`() {
        val sandboxedInvoker = sandboxLoader.getSandboxedInvoker()
        val locationFilter = FrameworkUtil.getBundle(sandboxedInvoker::class.java).location

        sandboxLoader.securityManagerService.grantPermission(locationFilter, setOf(getEnvPerm))

        assertDoesNotThrow {
            sandboxedInvoker.performActionRequiringGetEnvRuntimePermission()
        }
    }

    @Test
    fun `sandboxed bundle can be granted additional permissions using a wildcard match`() {
        val sandboxedInvoker = sandboxLoader.getSandboxedInvoker()
        val bundleLocation = FrameworkUtil.getBundle(sandboxedInvoker::class.java).location
        val wildcardFilter = bundleLocation.dropLast(5) + WILDCARD_MATCH

        sandboxLoader.securityManagerService.grantPermission(wildcardFilter, setOf(getEnvPerm))

        assertDoesNotThrow {
            sandboxedInvoker.performActionRequiringGetEnvRuntimePermission()
        }
    }

    @Test
    fun `sandboxed bundle can be granted multiple additional permissions at once`() {
        val sandboxedInvoker = sandboxLoader.getSandboxedInvoker()
        val locationFilter = FrameworkUtil.getBundle(sandboxedInvoker::class.java).location

        sandboxLoader.securityManagerService.grantPermission(locationFilter, setOf(getEnvPerm, getProtectionDomainPerm))

        assertDoesNotThrow {
            sandboxedInvoker.performActionRequiringGetEnvRuntimePermission()
            sandboxedInvoker.performActionRequiringGetProtectionDomainRuntimePermission()
        }
    }

    @Test
    fun `additional permissions are reset once the security manager is restarted`() {
        val sandboxedInvoker = sandboxLoader.getSandboxedInvoker()
        val locationFilter = FrameworkUtil.getBundle(sandboxedInvoker::class.java).location
        sandboxLoader.securityManagerService.grantPermission(locationFilter, setOf(getEnvPerm))

        // This stops the existing `RestrictiveSecurityManager`, and starts a new one.
        sandboxLoader.securityManagerService.start()

        assertThrows<AccessControlException> {
            sandboxedInvoker.performActionRequiringGetEnvRuntimePermission()
        }
    }
}