package net.corda.securitymanager.internal

import net.corda.securitymanager.SecurityManagerService
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import org.osgi.framework.FrameworkUtil
import org.osgi.test.common.annotation.InjectService
import org.osgi.test.junit5.service.ServiceExtension
import java.security.AccessControlException

/** Tests the `RestrictiveSecurityManager`. */
@ExtendWith(ServiceExtension::class)
class RestrictiveSecurityManagerTests {
    companion object {
        // The permission to get any environment variable.
        private val getEnvPerm = RuntimePermission(GET_ENV_TARGET, null)

        // The permission to get a class's protection domain.
        private val getProtectionDomainPerm = RuntimePermission(GET_PROTECTION_DOMAIN_TARGET, null)

        private val currentBundleLocation = FrameworkUtil.getBundle(this::class.java).location
    }

    @InjectService(timeout = 1000)
    lateinit var securityManagerService: SecurityManagerService

    @Suppress("unused")
    @BeforeEach
    fun reset() {
        securityManagerService.start()
        assertNotNull(System.getSecurityManager())
    }

    @Test
    fun `no permissions are denied by default`() {
        assertDoesNotThrow {
            // This permission stands in for all permissions.
            System.getenv()
        }
    }

    @Test
    fun `specific permissions can be denied`() {
        securityManagerService.denyPermissions(currentBundleLocation, setOf(getEnvPerm))

        assertThrows<AccessControlException> {
            System.getenv()
        }
    }

    @Test
    fun `multiple permissions can be denied at once`() {
        securityManagerService.denyPermissions(currentBundleLocation, setOf(getEnvPerm, getProtectionDomainPerm))

        assertThrows<AccessControlException> {
            System.getenv()
        }
        assertThrows<AccessControlException> {
            Any::class.java.protectionDomain
        }
    }

    @Test
    fun `multiple permissions can be denied in sequence`() {
        securityManagerService.denyPermissions(currentBundleLocation, setOf(getEnvPerm))
        securityManagerService.denyPermissions(currentBundleLocation, setOf(getProtectionDomainPerm))

        assertThrows<AccessControlException> {
            System.getenv()
        }
        assertThrows<AccessControlException> {
            Any::class.java.protectionDomain
        }
    }

    @Test
    fun `specific permissions can be granted`() {
        // We deny a permission, then re-grant it.
        securityManagerService.denyPermissions(currentBundleLocation, setOf(getEnvPerm))
        securityManagerService.grantPermissions(currentBundleLocation, setOf(getEnvPerm))

        assertDoesNotThrow {
            System.getenv()
        }
    }

    @Test
    fun `multiple permissions can be granted at once`() {
        // We deny permissions, then re-grant them.
        securityManagerService.denyPermissions(currentBundleLocation, setOf(getEnvPerm, getProtectionDomainPerm))
        securityManagerService.grantPermissions(currentBundleLocation, setOf(getEnvPerm, getProtectionDomainPerm))

        assertDoesNotThrow {
            System.getenv()
            Any::class.java.protectionDomain
        }
    }

    @Test
    fun `multiple permissions can be granted in sequence`() {
        securityManagerService.denyPermissions(currentBundleLocation, setOf(getEnvPerm, getProtectionDomainPerm))
        securityManagerService.grantPermissions(currentBundleLocation, setOf(getEnvPerm))
        securityManagerService.grantPermissions(currentBundleLocation, setOf(getProtectionDomainPerm))

        assertDoesNotThrow {
            System.getenv()
            Any::class.java.protectionDomain
        }
    }

    @Test
    fun `specific permissions can be denied using wildcards`() {
        val wildcardLocation = currentBundleLocation.dropLast(5) + WILDCARD

        securityManagerService.denyPermissions(wildcardLocation, setOf(getEnvPerm))

        assertThrows<AccessControlException> {
            System.getenv()
        }
    }

    @Test
    fun `specific permissions can be granted using wildcards`() {
        val wildcardLocation = currentBundleLocation.dropLast(5) + WILDCARD

        // We deny a permission, then re-grant it.
        securityManagerService.denyPermissions(currentBundleLocation, setOf(getEnvPerm))
        securityManagerService.grantPermissions(wildcardLocation, setOf(getEnvPerm))

        assertDoesNotThrow {
            System.getenv()
        }
    }

    @Test
    fun `later permissions overwrite earlier permissions`() {
        securityManagerService.denyPermissions(currentBundleLocation, setOf(getEnvPerm))

        securityManagerService.grantPermissions(currentBundleLocation, setOf(getEnvPerm))
        assertDoesNotThrow {
            System.getenv()
        }

        securityManagerService.denyPermissions(currentBundleLocation, setOf(getEnvPerm))
        assertThrows<AccessControlException> {
            System.getenv()
        }
    }

    @Test
    fun `denied permissions do not affect bundles not matching the filter`() {
        securityManagerService.denyPermissions("non-matching-filter", setOf(getEnvPerm))

        assertDoesNotThrow {
            System.getenv()
        }
    }

    @Test
    fun `granted permissions do not affect bundles not matching the filter`() {
        securityManagerService.denyPermissions(currentBundleLocation, setOf(getEnvPerm))
        securityManagerService.grantPermissions("non-matching-filter", setOf(getEnvPerm))

        assertThrows<AccessControlException> {
            System.getenv()
        }
    }

    @Test
    fun `permissions are reset once the security manager is restarted`() {
        securityManagerService.denyPermissions(currentBundleLocation, setOf(getEnvPerm))

        // This stops the existing `RestrictiveSecurityManager`, and starts a new one.
        securityManagerService.start()

        assertDoesNotThrow {
            System.getenv()
        }
    }

    @Test
    fun `permissions are reset once the security manager is switched to the discovery security manager`() {
        securityManagerService.denyPermissions(currentBundleLocation, setOf(getEnvPerm))

        // This stops the existing `RestrictiveSecurityManager`, and starts the `DiscoverySecurityManager`.
        securityManagerService.startDiscoveryMode(setOf())

        assertDoesNotThrow {
            System.getenv()
        }
    }

    @Test
    fun `the OSGi security manager is set again when the restrictive security manager is started`() {
        System.setSecurityManager(null)

        securityManagerService.start()
        securityManagerService.denyPermissions(currentBundleLocation, setOf(getEnvPerm))

        assertThrows<AccessControlException> {
            System.getenv()
        }
    }
}