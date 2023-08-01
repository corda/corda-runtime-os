package net.corda.securitymanager.internal

import net.corda.securitymanager.SecurityManagerService
import net.corda.testing.securitymanager.denyPermissions
import net.corda.testing.securitymanager.grantPermissions
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import org.osgi.framework.FrameworkUtil
import org.osgi.test.common.annotation.InjectService
import org.osgi.test.junit5.service.ServiceExtension

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
        securityManagerService.startRestrictiveMode()
        @Suppress("deprecation", "removal")
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
        securityManagerService.denyPermissions(currentBundleLocation, listOf(getEnvPerm))

        @Suppress("deprecation", "removal")
        assertThrows<java.security.AccessControlException> {
            System.getenv()
        }
    }

    @Test
    fun `multiple permissions can be denied at once`() {
        securityManagerService.denyPermissions(currentBundleLocation, listOf(getEnvPerm, getProtectionDomainPerm))

        @Suppress("deprecation", "removal")
        assertThrows<java.security.AccessControlException> {
            System.getenv()
        }
        @Suppress("deprecation", "removal")
        assertThrows<java.security.AccessControlException> {
            Any::class.java.protectionDomain
        }
    }

    @Test
    fun `multiple permissions can be denied in sequence`() {
        securityManagerService.denyPermissions(currentBundleLocation, listOf(getEnvPerm))
        securityManagerService.denyPermissions(currentBundleLocation, listOf(getProtectionDomainPerm))

        @Suppress("deprecation", "removal")
        assertThrows<java.security.AccessControlException> {
            System.getenv()
        }
        @Suppress("deprecation", "removal")
        assertThrows<java.security.AccessControlException> {
            Any::class.java.protectionDomain
        }
    }

    @Test
    fun `specific permissions can be granted`() {
        // We deny a permission, then re-grant it.
        securityManagerService.denyPermissions(currentBundleLocation, listOf(getEnvPerm))
        securityManagerService.grantPermissions(currentBundleLocation, listOf(getEnvPerm))

        assertDoesNotThrow {
            System.getenv()
        }
    }

    @Test
    fun `multiple permissions can be granted at once`() {
        // We deny permissions, then re-grant them.
        securityManagerService.denyPermissions(currentBundleLocation, listOf(getEnvPerm, getProtectionDomainPerm))
        securityManagerService.grantPermissions(currentBundleLocation, listOf(getEnvPerm, getProtectionDomainPerm))

        assertDoesNotThrow {
            System.getenv()
            Any::class.java.protectionDomain
        }
    }

    @Test
    fun `multiple permissions can be granted in sequence`() {
        securityManagerService.denyPermissions(currentBundleLocation, listOf(getEnvPerm, getProtectionDomainPerm))
        securityManagerService.grantPermissions(currentBundleLocation, listOf(getEnvPerm))
        securityManagerService.grantPermissions(currentBundleLocation, listOf(getProtectionDomainPerm))

        assertDoesNotThrow {
            System.getenv()
            Any::class.java.protectionDomain
        }
    }

    @Test
    fun `specific permissions can be denied using wildcards`() {
        val wildcardLocation = currentBundleLocation.dropLast(5) + WILDCARD

        securityManagerService.denyPermissions(wildcardLocation, listOf(getEnvPerm))

        @Suppress("deprecation", "removal")
        assertThrows<java.security.AccessControlException> {
            System.getenv()
        }
    }

    @Test
    fun `specific permissions can be granted using wildcards`() {
        val wildcardLocation = currentBundleLocation.dropLast(5) + WILDCARD

        // We deny a permission, then re-grant it.
        securityManagerService.denyPermissions(currentBundleLocation, listOf(getEnvPerm))
        securityManagerService.grantPermissions(wildcardLocation, listOf(getEnvPerm))

        assertDoesNotThrow {
            System.getenv()
        }
    }

    @Test
    fun `later permissions overwrite earlier permissions`() {
        securityManagerService.denyPermissions(currentBundleLocation, listOf(getEnvPerm))

        securityManagerService.grantPermissions(currentBundleLocation, listOf(getEnvPerm))
        assertDoesNotThrow {
            System.getenv()
        }

        securityManagerService.denyPermissions(currentBundleLocation, listOf(getEnvPerm))
        @Suppress("deprecation", "removal")
        assertThrows<java.security.AccessControlException> {
            System.getenv()
        }
    }

    @Test
    fun `denied permissions do not affect bundles not matching the filter`() {
        securityManagerService.denyPermissions("non-matching-filter", listOf(getEnvPerm))

        assertDoesNotThrow {
            System.getenv()
        }
    }

    @Test
    fun `granted permissions do not affect bundles not matching the filter`() {
        securityManagerService.denyPermissions(currentBundleLocation, listOf(getEnvPerm))
        securityManagerService.grantPermissions("non-matching-filter", listOf(getEnvPerm))

        @Suppress("deprecation", "removal")
        assertThrows<java.security.AccessControlException> {
            System.getenv()
        }
    }

    @Test
    fun `permissions are reset once the security manager is restarted`() {
        securityManagerService.denyPermissions(currentBundleLocation, listOf(getEnvPerm))

        // This stops the existing `RestrictiveSecurityManager`, and starts a new one.
        securityManagerService.startRestrictiveMode()

        assertDoesNotThrow {
            System.getenv()
        }
    }

    @Test
    fun `the OSGi security manager is set again when the restrictive security manager is started`() {
        @Suppress("deprecation", "removal")
        System.setSecurityManager(null)

        securityManagerService.startRestrictiveMode()
        securityManagerService.denyPermissions(currentBundleLocation, listOf(getEnvPerm))

        @Suppress("deprecation", "removal")
        assertThrows<java.security.AccessControlException> {
            System.getenv()
        }
    }
}