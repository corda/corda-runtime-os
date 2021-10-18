package net.corda.securitymanager.osgi

import net.corda.securitymanager.localpermissions.LocalPermissions
import net.corda.securitymanager.osgiinvoker.OsgiInvoker
import net.corda.securitymanager.osgiinvokerimpl.OsgiInvokerImpl
import org.junit.jupiter.api.Assertions.assertDoesNotThrow
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.fail
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import org.osgi.framework.Bundle
import org.osgi.framework.Constants.SYSTEM_BUNDLE_ID
import org.osgi.framework.FrameworkUtil
import org.osgi.test.common.annotation.InjectService
import org.osgi.test.junit5.service.ServiceExtension
import java.net.URL
import java.security.AccessControlException
import kotlin.math.abs
import kotlin.random.Random

/** Tests the permissions of sandboxed bundles. */
@ExtendWith(ServiceExtension::class)
class BundlePermissionTests {
    companion object {
        private const val SANDBOX_BUNDLE_PREFIX = "sandbox/"
        private const val TEST_BUNDLE_LOCATION_PREFIX = "reference:"

        @InjectService(timeout = 1000)
        lateinit var unsandboxedOsgiInvoker: OsgiInvoker

        lateinit var sandboxedOsgiInvoker: OsgiInvoker

        @InjectService(timeout = 1000)
        lateinit var unsandboxedLocalPermissions: LocalPermissions

        lateinit var sandboxedLocalPermisions: LocalPermissions

        @Suppress("unused")
        @JvmStatic
        @BeforeAll
        fun setup() {
            val sandboxedOsgiInvokerBundle = createSandboxedBundle(unsandboxedOsgiInvoker::class.java)
            sandboxedOsgiInvoker = retrieveServiceFromBundle(sandboxedOsgiInvokerBundle, OsgiInvoker::class.java)

            val sandboxedLocalPermissionsBundle = createSandboxedBundle(unsandboxedLocalPermissions::class.java)
            sandboxedLocalPermisions = retrieveServiceFromBundle(sandboxedLocalPermissionsBundle, LocalPermissions::class.java)
        }

        /** Returns a sandboxed [Bundle] installed from the same source as the bundle containing [classFromBundle].  */
        @Suppress("unchecked_cast")
        private fun <T> createSandboxedBundle(classFromBundle: Class<T>): Bundle {
            // We retrieve the location of the existing bundle that contains `OsgiInvokerImpl`.
            val unsandboxedBundle = FrameworkUtil.getBundle(classFromBundle)
            val unsandboxedBundleLocation = URL(unsandboxedBundle.location.removePrefix(TEST_BUNDLE_LOCATION_PREFIX))
            val sandboxedBundleLocation = "$SANDBOX_BUNDLE_PREFIX${abs(Random.nextInt())}"

            // We install a fresh copy of the bundle into a sandbox, retrieving its contents from its original
            // location.
            val systemContext = unsandboxedBundle.bundleContext.getBundle(SYSTEM_BUNDLE_ID).bundleContext
            val sandboxedBundle = unsandboxedBundleLocation.openStream().use { inputStream ->
                systemContext.installBundle(sandboxedBundleLocation, inputStream)
            }
            sandboxedBundle.start()

            return sandboxedBundle
        }

        /** Retrieves a service implementing [serviceInterface] from [bundle]. */
        @Suppress("unchecked_cast")
        private fun <T> retrieveServiceFromBundle(bundle: Bundle, serviceInterface: Class<T>): T {
            val sandboxedBundleContext = bundle.bundleContext
            val sandboxedOsgiInvokerServiceReference = sandboxedBundleContext
                    .getAllServiceReferences(serviceInterface.name, null)
                    .firstOrNull { serviceReference -> serviceReference.bundle === bundle }
                    ?: fail("Could not retrieve service for interface $serviceInterface.")
            return sandboxedBundleContext.getService(sandboxedOsgiInvokerServiceReference) as T
        }
    }

    @Test
    fun `unsandboxed bundle has general permissions`() {
        assertDoesNotThrow {
            // A `RuntimePermission` is used here as a stand-in for testing all the various permissions and actions.
            unsandboxedOsgiInvoker.performActionRequiringRuntimePermission()
        }
    }

    @Test
    fun `sandboxed bundle does not have general permissions`() {
        assertThrows<AccessControlException> {
            // A `RuntimePermission` is used here as a stand-in for testing all the various permissions and actions.
            sandboxedOsgiInvoker.performActionRequiringRuntimePermission()
        }
    }

    @Test
    fun `sandboxed bundle has service get permissions`() {
        assertDoesNotThrow {
            sandboxedOsgiInvoker.performActionRequiringServiceGetPermission()
        }
    }

    @Test
    fun `sandboxed bundle has service register permissions`() {
        assertDoesNotThrow {
            sandboxedOsgiInvoker.performActionRequiringServiceRegisterPermission()
        }
    }

    // TODO - The following tests are removed as part of a separate PR, so aren't updated here.

    @Test
    fun `sandboxed local permissions bundle has context permissions`() {
        // The local permissions file grants this bundle "context" permissions.
        assertDoesNotThrow {
            sandboxedLocalPermisions.getBundleContext()
        }
    }

    @Test
    fun `sandboxed local permissions bundle bundle does not have execute permissions`() {
        assertThrows(AccessControlException::class.java) {
            sandboxedLocalPermisions.startBundle()
        }
    }

    @Test
    fun `sandboxed local permissions bundle does not have lifecycle permissions`() {
        // Although the local permissions file grants this bundle "lifecycle" permissions, these are still denied by
        // `CordaSecurityManager`.
        assertThrows(AccessControlException::class.java) {
            sandboxedLocalPermisions.installBundle()
        }
    }

    @Test
    fun `sandboxed local permissions bundle does not have listener permissions`() {
        assertThrows(AccessControlException::class.java) {
            sandboxedLocalPermisions.addListener()
        }
    }

    @Test
    fun `sandboxed local permissions bundle does not have class permissions`() {
        assertThrows(ClassNotFoundException::class.java) {
            sandboxedLocalPermisions.loadClass()
        }
    }

    @Test
    fun `sandboxed local permissions bundle does not have metadata permissions`() {
        assertThrows(AccessControlException::class.java) {
            sandboxedLocalPermisions.getLocation()
        }
    }

    @Test
    fun `sandboxed local permissions bundle does not have resolve permissions`() {
        assertThrows(AccessControlException::class.java) {
            sandboxedLocalPermisions.refreshBundles()
        }
    }

    @Test
    fun `sandboxed local permissions bundle does not have adapt permissions`() {
        assertThrows(AccessControlException::class.java) {
            sandboxedLocalPermisions.adaptBundle()
        }
    }

    @Test
    fun `sandboxed local permissions bundle does not have service permissions`() {
        assertThrows(NullPointerException::class.java) {
            sandboxedLocalPermisions.getService()
        }
    }

    @Test
    fun `sandboxed local permissions bundle does not have file permissions`() {
        // Any permission not explicitly granted in the local permissions file is denied.
        assertThrows(AccessControlException::class.java) {
            sandboxedLocalPermisions.readFile()
        }
    }
}