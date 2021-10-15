package net.corda.securitymanager.osgi

import net.corda.securitymanager.osgiinvoker.OsgiInvoker
import org.junit.jupiter.api.Assertions.assertDoesNotThrow
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.fail
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.osgi.framework.Bundle
import org.osgi.framework.FrameworkUtil
import org.osgi.test.common.annotation.InjectService
import org.osgi.test.junit5.service.ServiceExtension
import java.net.URL
import java.security.AccessControlException
import kotlin.random.Random

/** Tests the permissions of sandboxed bundles. */
@ExtendWith(ServiceExtension::class)
class BundlePermissionTests {
    companion object {
        private const val SANDBOX_SECURITY_DOMAIN_PREFIX = "sandbox/"
        private const val REFERENCE_PREFIX = "reference:"

        @InjectService(timeout = 1000)
        lateinit var unsandboxedOsgiInvoker: OsgiInvoker

        lateinit var sandboxedOsgiInvoker: OsgiInvoker

        @Suppress("unused")
        @JvmStatic
        @BeforeAll
        fun setup() {
            val sandboxedOsgiInvokerBundle = createSandboxedBundle(unsandboxedOsgiInvoker::class.java)
            sandboxedOsgiInvoker = retrieveServiceFromBundle(sandboxedOsgiInvokerBundle, OsgiInvoker::class.java)
        }

        /** Returns a sandboxed [Bundle] installed from the same source as the bundle containing [classFromBundle].  */
        @Suppress("unchecked_cast")
        private fun <T> createSandboxedBundle(classFromBundle: Class<T>): Bundle {
            // We retrieve the location of the unsandboxed bundle that contains `OsgiInvokerImpl`.
            val unsandboxedBundle = FrameworkUtil.getBundle(classFromBundle)
            val unsandboxedBundleLocation = URL(unsandboxedBundle.location.removePrefix(REFERENCE_PREFIX))

            // We install a copy of the unsandboxed bundle into a sandbox.
            val sandboxedBundleLocation = "$SANDBOX_SECURITY_DOMAIN_PREFIX${Random.nextInt()}"
            val sandboxedBundle = unsandboxedBundleLocation.openStream().use { inputStream ->
                unsandboxedBundle.bundleContext.installBundle(sandboxedBundleLocation, inputStream)
            }.apply { start() }

            return sandboxedBundle
        }

        /** Retrieves a service implementing [serviceInterface] from [bundle]. */
        @Suppress("unchecked_cast")
        private fun <T> retrieveServiceFromBundle(bundle: Bundle, serviceInterface: Class<T>): T {
            val serviceRef = bundle.registeredServices.firstOrNull { service -> service.bundle == bundle }
                ?: fail("Could not retrieve service for interface $serviceInterface from bundle $bundle.")
            return bundle.bundleContext.getService(serviceRef) as T
        }
    }

    @Test
    fun `unsandboxed bundle has context permissions`() {
        assertDoesNotThrow {
            unsandboxedOsgiInvoker.getBundleContext()
        }
    }

    @Test
    fun `unsandboxed bundle has execute permissions`() {
        assertDoesNotThrow {
            unsandboxedOsgiInvoker.startBundle()
        }
    }

    @Test
    fun `unsandboxed bundle has lifecycle permissions`() {
        assertDoesNotThrow {
            unsandboxedOsgiInvoker.installBundle()
        }
    }

    @Test
    fun `unsandboxed bundle has listener permissions`() {
        assertDoesNotThrow {
            unsandboxedOsgiInvoker.addListener()
        }
    }

    @Test
    fun `unsandboxed bundle has class permissions`() {
        assertDoesNotThrow {
            unsandboxedOsgiInvoker.loadClass()
        }
    }

    @Test
    fun `unsandboxed bundle has metadata permissions`() {
        assertDoesNotThrow {
            unsandboxedOsgiInvoker.getLocation()
        }
    }

    @Test
    fun `unsandboxed bundle has resolve permissions`() {
        assertDoesNotThrow {
            unsandboxedOsgiInvoker.refreshBundles()
        }
    }

    @Test
    fun `unsandboxed bundle has adapt permissions`() {
        assertDoesNotThrow {
            unsandboxedOsgiInvoker.adaptBundle()
        }
    }

    @Test
    fun `unsandboxed bundle has service permissions`() {
        assertDoesNotThrow {
            unsandboxedOsgiInvoker.getService()
        }
    }

    @Test
    fun `sandboxed bundle has context permissions`() {
        assertDoesNotThrow {
            sandboxedOsgiInvoker.getBundleContext()
        }
    }

    @Test
    fun `sandboxed bundle does not have execute permissions`() {
        assertThrows(AccessControlException::class.java) {
            sandboxedOsgiInvoker.startBundle()
        }
    }

    @Test
    fun `sandboxed bundle does not have lifecycle permissions`() {
        assertThrows(AccessControlException::class.java) {
            sandboxedOsgiInvoker.installBundle()
        }
    }

    @Test
    fun `sandboxed bundle has listener permissions`() {
        assertDoesNotThrow {
            sandboxedOsgiInvoker.addListener()
        }
    }

    @Test
    fun `sandboxed bundle has class permissions`() {
        assertDoesNotThrow {
            sandboxedOsgiInvoker.loadClass()
        }
    }

    @Test
    fun `sandboxed bundle has metadata permissions`() {
        assertDoesNotThrow {
            sandboxedOsgiInvoker.getLocation()
        }
    }

    @Test
    fun `sandboxed bundle does not have resolve permissions`() {
        assertThrows(AccessControlException::class.java) {
            sandboxedOsgiInvoker.refreshBundles()
        }
    }

    @Test
    fun `sandboxed bundle has adapt permissions`() {
        assertDoesNotThrow {
            sandboxedOsgiInvoker.adaptBundle()
        }
    }

    @Test
    fun `sandboxed bundle has service permissions`() {
        assertDoesNotThrow {
            sandboxedOsgiInvoker.getService()
        }
    }
}