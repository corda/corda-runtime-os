package net.corda.securitymanager.osgi

import net.corda.securitymanager.SecurityManagerService
import net.corda.securitymanager.localpermissions.LocalPermissions
import net.corda.securitymanager.osgiinvoker.OsgiInvoker
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.fail
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.api.extension.ExtendWith
import org.osgi.framework.Bundle
import org.osgi.framework.Constants.SYSTEM_BUNDLE_ID
import org.osgi.framework.FrameworkUtil
import org.osgi.test.common.annotation.InjectService
import org.osgi.test.junit5.service.ServiceExtension
import java.net.URL
import java.security.AllPermission
import java.security.CodeSource
import java.security.Permissions
import java.security.Policy
import kotlin.math.abs
import kotlin.random.Random

/** Tests the permissions of sandboxed bundles. */
@ExtendWith(ServiceExtension::class)
class DiscoveryPermissionTests {
    companion object {
        private const val SANDBOX_BUNDLE_PREFIX = "sandbox/"
        private const val TEST_BUNDLE_LOCATION_PREFIX = "reference:"

        @InjectService(timeout = 1000)
        lateinit var securityManagerService: SecurityManagerService

        @InjectService(timeout = 1000)
        lateinit var unsandboxedOsgiInvoker: OsgiInvoker

        @InjectService(timeout = 1000)
        lateinit var unsandboxedLocalPermissions: LocalPermissions

        lateinit var sandboxedOsgiInvoker: OsgiInvoker

        lateinit var sandboxedLocalPermisions: LocalPermissions

        // TODO - These various methods need some consolidation, but this is best done once PR #366 is merged.

        @Suppress("unused")
        @JvmStatic
        @BeforeAll
        fun setup() {
            val sandboxedOsgiInvokerBundle = createSandboxedBundle(unsandboxedOsgiInvoker::class.java)
            sandboxedOsgiInvoker = retrieveServiceFromBundle(sandboxedOsgiInvokerBundle, OsgiInvoker::class.java)

            val sandboxedLocalPermissionsBundle = createSandboxedBundle(unsandboxedLocalPermissions::class.java)
            sandboxedLocalPermisions =
                retrieveServiceFromBundle(sandboxedLocalPermissionsBundle, LocalPermissions::class.java)

            securityManagerService.start(isDiscoveryMode = true)
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
    fun `discovery mode grants all OSGi permissions`() {
        assertDoesNotThrow {
            sandboxedOsgiInvoker.apply {
                getBundleContext()
                startBundle()
                installBundle()
                addListener()
                loadClass()
                getLocation()
                refreshBundles()
                adaptBundle()
                getService()
            }
        }
    }
}