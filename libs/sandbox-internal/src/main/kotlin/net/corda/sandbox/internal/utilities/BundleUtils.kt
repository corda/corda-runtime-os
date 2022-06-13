package net.corda.sandbox.internal.utilities

import org.osgi.framework.Bundle
import org.osgi.framework.BundleContext
import org.osgi.framework.Constants.SYSTEM_BUNDLE_ID
import org.osgi.framework.FrameworkListener
import org.osgi.framework.FrameworkUtil
import org.osgi.framework.wiring.FrameworkWiring
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import org.osgi.service.component.runtime.ServiceComponentRuntime

/** Handles bundle operations for the `SandboxCreationService` and the `SandboxContextService`. */
@Component(service = [BundleUtils::class])
internal class BundleUtils @Activate constructor(
    @Reference
    private val serviceComponentRuntime: ServiceComponentRuntime,
    private val bundleContext: BundleContext
) {
    private val systemBundle = bundleContext.getBundle(SYSTEM_BUNDLE_ID)

    /** Returns the bundle from which [klass] is loaded, or null if there is no such bundle. */
    fun getBundle(klass: Class<*>): Bundle? = FrameworkUtil.getBundle(klass) ?: try {
        // The lookup approach above does not work for the system bundle.
        if (loadClassFromSystemBundle(klass.name) === klass) systemBundle else null
    } catch (e: ClassNotFoundException) {
        null
    }

    /** Loads OSGi framework types. Can also be used to load Java platform types. */
    fun loadClassFromSystemBundle(className: String): Class<*> = systemBundle.loadClass(className)

    /**
     * Returns the bundle from which [serviceComponentRuntime] is loaded, or null if there is no such bundle.
     *
     * This exists to simplify mocking - we can provide one mock for recovering the `ServiceComponentRuntime` bundle
     * during `SandboxServiceImpl` initialisation, and another mock for general retrieval of bundles based on classes.
     */
    fun getServiceRuntimeComponentBundle(): Bundle? = FrameworkUtil.getBundle(serviceComponentRuntime::class.java)

    /** Returns the list of all installed bundles. */
    val allBundles get() = bundleContext.bundles.toList()

    /**
     * Force the update or removal of bundles.
     */
    fun refreshBundles(bundles: Collection<Bundle>, refreshListener: FrameworkListener) {
        systemBundle.adapt(FrameworkWiring::class.java).refreshBundles(bundles, refreshListener)
    }
}
