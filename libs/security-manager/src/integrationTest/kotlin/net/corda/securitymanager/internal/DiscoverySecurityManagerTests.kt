package net.corda.securitymanager.internal

import net.corda.securitymanager.SecurityManagerService
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.api.extension.ExtendWith
import org.osgi.framework.FrameworkUtil
import org.osgi.test.common.annotation.InjectService
import org.osgi.test.junit5.service.ServiceExtension

/** Tests the `DiscoverySecurityManager`. */
@ExtendWith(ServiceExtension::class)
class DiscoverySecurityManagerTests {
    companion object {
        // The permission to get any environment variable.
        private val getEnvPerm = RuntimePermission(GET_ENV_TARGET, null)

        private val bundleLocation = FrameworkUtil.getBundle(this::class.java).location
    }

    @InjectService(timeout = 1000)
    lateinit var securityManagerService: SecurityManagerService

    @Suppress("unused")
    @BeforeEach
    fun reset() {
        securityManagerService.startDiscoveryMode(setOf(bundleLocation))
    }

    @Test
    fun `no permissions are denied by default`() {
        assertDoesNotThrow {
            // This permission stands in for all permissions.
            System.getenv()
        }
    }

    @Test
    fun `denying permissions has no effect`() {
        securityManagerService.denyPermissions(bundleLocation, setOf(getEnvPerm))

        assertDoesNotThrow {
            System.getenv()
        }
    }
}