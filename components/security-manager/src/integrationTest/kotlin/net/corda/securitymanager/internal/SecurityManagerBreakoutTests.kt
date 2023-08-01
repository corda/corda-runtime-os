package net.corda.securitymanager.internal

import net.corda.securitymanager.SecurityManagerService
import net.corda.testing.securitymanager.denyPermissions
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import org.osgi.framework.FrameworkUtil
import org.osgi.service.condpermadmin.ConditionalPermissionAdmin
import org.osgi.service.permissionadmin.PermissionAdmin
import org.osgi.test.common.annotation.InjectService
import org.osgi.test.junit5.service.ServiceExtension
import java.security.AllPermission

/** Tests that the `RestrictiveSecurityManager` cannot be bypassed. */
@ExtendWith(ServiceExtension::class)
class SecurityManagerBreakoutTests {
    companion object {
        private val currentBundleLocation = FrameworkUtil.getBundle(this::class.java).location
        private val permAdmin = getService(PermissionAdmin::class.java)
        private val condPermAdmin = getService(ConditionalPermissionAdmin::class.java)

        @InjectService(timeout = 1000)
        lateinit var securityManagerService: SecurityManagerService

        @Suppress("unused")
        @JvmStatic
        @BeforeAll
        fun setup() {
            securityManagerService.startRestrictiveMode()
            @Suppress("deprecation", "removal")
            assertNotNull(System.getSecurityManager())
            securityManagerService.denyPermissions(currentBundleLocation, listOf(AllPermission()))
        }

        /** Returns an instance of [service] from the OSGi service registry. */
        private fun <T> getService(service: Class<T>): T {
            val bundleContext = FrameworkUtil.getBundle(this::class.java).bundleContext
            val serviceRef = bundleContext.getServiceReference(service)
            return bundleContext.getService(serviceRef)!!
        }
    }

    @Test
    fun `a bundle without AllPermission cannot modify permissions via the permission admin`() {
        @Suppress("deprecation", "removal")
        assertThrows<java.security.AccessControlException> {
            permAdmin.setPermissions("", arrayOf())
        }
    }

    @Test
    fun `a bundle without AllPermission cannot modify permissions via the conditional permission admin`() {
        @Suppress("deprecation", "removal")
        assertThrows<java.security.AccessControlException> {
            condPermAdmin.newConditionalPermissionUpdate().commit()
        }
    }

    @Test
    fun `a bundle without AllPermission cannot unset the security manager`() {
        @Suppress("deprecation", "removal")
        assertThrows<java.security.AccessControlException> {
            System.setSecurityManager(null)
        }
    }

    @Test
    fun `a bundle without AllPermission cannot replace the security manager`() {
        @Suppress("deprecation", "removal")
        assertThrows<java.security.AccessControlException> {
            System.setSecurityManager(SecurityManager())
        }
    }
}