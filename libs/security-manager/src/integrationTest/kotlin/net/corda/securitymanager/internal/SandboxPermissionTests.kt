package net.corda.securitymanager.internal

import org.junit.jupiter.api.Assertions.assertDoesNotThrow
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
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
        assertThrows<AccessControlException> {
            // A `RuntimePermission` is used here as a stand-in for testing all the various permissions and actions.
            sandboxLoader.sandboxedInvoker.performActionRequiringRuntimePermission()
        }
    }

    @Test
    fun `sandboxed bundle has service get permissions`() {
        assertDoesNotThrow {
            sandboxLoader.sandboxedInvoker.performActionRequiringServiceGetPermission()
        }
    }

    @Test
    fun `sandboxed bundle has service register permissions`() {
        assertDoesNotThrow {
            sandboxLoader.sandboxedInvoker.performActionRequiringServiceRegisterPermission()
        }
    }
}