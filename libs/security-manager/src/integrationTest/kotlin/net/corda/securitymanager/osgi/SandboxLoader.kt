package net.corda.securitymanager.osgi

import net.corda.securitymanager.SecurityManagerService
import net.corda.securitymanager.osgiinvoker.OsgiInvoker
import org.junit.jupiter.api.Assertions
import org.osgi.framework.Bundle
import org.osgi.framework.Constants
import org.osgi.framework.FrameworkUtil
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import java.net.URL
import kotlin.math.abs
import kotlin.random.Random

/** Helpers for working with bundles. */
@Component(service = [SandboxLoader::class])
class SandboxLoader @Activate constructor(
    @Reference
    val securityManagerService: SecurityManagerService,
    @Reference
    val unsandboxedOsgiInvoker: OsgiInvoker
) {
    companion object {
        private const val SANDBOX_SECURITY_DOMAIN_PREFIX = "sandbox/"
        private const val TEST_BUNDLE_LOCATION_PREFIX = "reference:"

        /** Returns a sandboxed [Bundle] with the same source as the bundle containing [classFromSourceBundle]. */
        @Suppress("unchecked_cast")
        internal fun <T> createSandboxedBundle(classFromSourceBundle: Class<T>): Bundle {
            // We retrieve the location of the existing bundle that contains `OsgiInvokerImpl`.
            val unsandboxedBundle = FrameworkUtil.getBundle(classFromSourceBundle)
            val unsandboxedBundleLocation =
                URL(unsandboxedBundle.location.removePrefix(TEST_BUNDLE_LOCATION_PREFIX))
            val sandboxedBundleLocation =
                "$SANDBOX_SECURITY_DOMAIN_PREFIX${abs(Random.nextInt())}"

            // We install a fresh copy of the bundle into a sandbox, retrieving its contents from its original
            // location.
            val systemContext = unsandboxedBundle.bundleContext.getBundle(Constants.SYSTEM_BUNDLE_ID).bundleContext
            val sandboxedBundle = unsandboxedBundleLocation.openStream().use { inputStream ->
                systemContext.installBundle(sandboxedBundleLocation, inputStream)
            }
            sandboxedBundle.start()

            return sandboxedBundle
        }

        /** Retrieves a service implementing [serviceInterface] from [bundle]. */
        @Suppress("unchecked_cast")
        internal fun <T> retrieveServiceFromBundle(bundle: Bundle, serviceInterface: Class<T>): T {
            val sandboxedBundleContext = bundle.bundleContext
            val sandboxedOsgiInvokerServiceReference = sandboxedBundleContext
                .getAllServiceReferences(serviceInterface.name, null)
                .firstOrNull { serviceReference -> serviceReference.bundle === bundle }
                ?: Assertions.fail("Could not retrieve service for interface $serviceInterface.")
            return sandboxedBundleContext.getService(sandboxedOsgiInvokerServiceReference) as T
        }
    }

    // An `OsgiInvoker` from a sandboxed bundle.
    val sandboxedOsgiInvoker: OsgiInvoker
    init {
        val sandboxedOsgiInvokerBundle = createSandboxedBundle(unsandboxedOsgiInvoker::class.java)
        sandboxedOsgiInvoker = retrieveServiceFromBundle(sandboxedOsgiInvokerBundle, OsgiInvoker::class.java)
    }
}