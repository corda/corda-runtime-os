package net.corda.securitymanager.osgi

import org.junit.jupiter.api.Assertions.assertDoesNotThrow
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
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
    fun `sandboxed bundle has context permissions`() {
        assertDoesNotThrow {
            sandboxLoader.sandboxedOsgiInvoker.getBundleContext()
        }
    }

    @Test
    fun `sandboxed bundle does not have execute permissions`() {
        assertThrows(AccessControlException::class.java) {
            sandboxLoader.sandboxedOsgiInvoker.startBundle()
        }
    }

    @Test
    fun `sandboxed bundle does not have lifecycle permissions`() {
        assertThrows(AccessControlException::class.java) {
            sandboxLoader.sandboxedOsgiInvoker.installBundle()
        }
    }

    @Test
    fun `sandboxed bundle has listener permissions`() {
        assertDoesNotThrow {
            sandboxLoader.sandboxedOsgiInvoker.addListener()
        }
    }

    @Test
    fun `sandboxed bundle has class permissions`() {
        assertDoesNotThrow {
            sandboxLoader.sandboxedOsgiInvoker.loadClass()
        }
    }

    @Test
    fun `sandboxed bundle has metadata permissions`() {
        assertDoesNotThrow {
            sandboxLoader.sandboxedOsgiInvoker.getLocation()
        }
    }

    @Test
    fun `sandboxed bundle does not have resolve permissions`() {
        assertThrows(AccessControlException::class.java) {
            sandboxLoader.sandboxedOsgiInvoker.refreshBundles()
        }
    }

    @Test
    fun `sandboxed bundle has adapt permissions`() {
        assertDoesNotThrow {
            sandboxLoader.sandboxedOsgiInvoker.adaptBundle()
        }
    }

    @Test
    fun `sandboxed bundle has service permissions`() {
        assertDoesNotThrow {
            sandboxLoader.sandboxedOsgiInvoker.getService()
        }
    }
}