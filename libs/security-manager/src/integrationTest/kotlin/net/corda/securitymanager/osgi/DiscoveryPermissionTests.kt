package net.corda.securitymanager.osgi

import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.api.extension.ExtendWith
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
            sandboxLoader.securityManagerService.start(isDiscoveryMode = true)
        }
    }

    @Test
    fun `discovery mode grants all OSGi permissions`() {
        val sandboxedInvoker = sandboxLoader.getSandboxedInvoker()
        assertDoesNotThrow {
            sandboxedInvoker.apply {
                performActionRequiringGetEnvRuntimePermission()
                performActionRequiringServiceGetPermission()
                performActionRequiringServiceRegisterPermission()
            }
        }
    }
}