package net.corda.securitymanager.internal

import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.api.extension.ExtendWith
import org.osgi.framework.FrameworkUtil
import org.osgi.test.common.annotation.InjectService
import org.osgi.test.junit5.service.ServiceExtension

/** Tests the permissions of sandboxed bundles in discovery mode. */
@ExtendWith(ServiceExtension::class)
class DiscoveryPermissionTests {
    companion object {
        @InjectService(timeout = 1000)
        lateinit var sandboxLoader: SandboxLoader

        @Suppress("unused")
        @JvmStatic
        @BeforeAll
        fun setup() {
            val bundleLocation = FrameworkUtil.getBundle(this::class.java).location
            sandboxLoader.securityManagerService.startDiscoveryMode(setOf(bundleLocation))
        }
    }

    @Test
    fun `no permissions are denied by default`() {
        assertDoesNotThrow {
            sandboxLoader.sandboxedInvoker.apply {
                performActionRequiringRuntimePermission()
                performActionRequiringServiceGetPermission()
                performActionRequiringServiceRegisterPermission()
            }
        }
    }

    // TODO - Add tests of logging.
    // TODO - Add tests of multiple prefixes.
}