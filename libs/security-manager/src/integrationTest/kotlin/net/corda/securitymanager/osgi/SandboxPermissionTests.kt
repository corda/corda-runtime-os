package net.corda.securitymanager.osgi

import net.corda.securitymanager.invoker.Invoker
import org.junit.jupiter.api.Assertions.fail
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.api.assertThrows
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
class SandboxPermissionTests {
    companion object {
        private const val SANDBOX_SECURITY_DOMAIN_PREFIX = "sandbox/"
        private const val REFERENCE_PREFIX = "reference:"

        @InjectService(timeout = 1000)
        lateinit var unsandboxedInvoker: Invoker

        lateinit var sandboxedInvoker: Invoker

        @Suppress("unused")
        @JvmStatic
        @BeforeAll
        fun setup() {
            val sandboxedOsgiInvokerBundle = createSandboxedBundle(unsandboxedInvoker::class.java)
            sandboxedInvoker = retrieveServiceFromBundle(sandboxedOsgiInvokerBundle, Invoker::class.java)
        }

        /** Returns a sandboxed [Bundle] installed from the same source as the bundle containing [classFromBundle].  */
        @Suppress("unchecked_cast")
        private fun <T> createSandboxedBundle(classFromBundle: Class<T>): Bundle {
            // We retrieve the location of the unsandboxed bundle that contains `InvokerImpl`.
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
    fun `sandboxed bundle does not have general permissions`() {
        assertThrows<AccessControlException> {
            // A `RuntimePermission` is used here as a stand-in for testing all the various permissions and actions.
            sandboxedInvoker.performActionRequiringRuntimePermission()
        }
    }

    @Test
    fun `sandboxed bundle has service get permissions`() {
        assertDoesNotThrow {
            sandboxedInvoker.performActionRequiringServiceGetPermission()
        }
    }

    @Test
    fun `sandboxed bundle has service register permissions`() {
        assertDoesNotThrow {
            sandboxedInvoker.performActionRequiringServiceRegisterPermission()
        }
    }
}