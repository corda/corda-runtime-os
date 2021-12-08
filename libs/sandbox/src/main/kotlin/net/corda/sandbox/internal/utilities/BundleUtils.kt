package net.corda.sandbox.internal.utilities

import org.osgi.framework.Bundle
import org.osgi.framework.BundleContext
import org.osgi.framework.BundleException
import org.osgi.framework.Constants.SYSTEM_BUNDLE_ID
import org.osgi.framework.FrameworkUtil
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import org.osgi.service.component.runtime.ServiceComponentRuntime
import java.io.InputStream
import java.security.AccessController.doPrivileged
import java.security.PrivilegedActionException
import java.security.PrivilegedExceptionAction

/** Handles bundle operations for the `SandboxCreationService` and the `SandboxContextService`. */
@Component(service = [BundleUtils::class])
internal class BundleUtils @Activate constructor(
    @Reference
    private val serviceComponentRuntime: ServiceComponentRuntime,
    private val bundleContext: BundleContext
) {
    private val systemBundle = bundleContext.getBundle(SYSTEM_BUNDLE_ID)

    /**
     * Installs the contents of the [inputStream] as a bundle, using the [location] provided.
     *
     * A [BundleException] is thrown if the bundle fails to install.
     */
    fun installAsBundle(location: String, inputStream: InputStream): Bundle = inputStream.use {
        // CorDapp code will call this method indirectly when creating transaction verification sandboxes. The use of
        // `doPrivileged` here prevents the limited permissions of the calling code (i.e. the CorDapp code) from
        // causing this operation to be denied.
        try {
            doPrivileged(PrivilegedExceptionAction {
                bundleContext.installBundle(location, it)
            })
        } catch (e: PrivilegedActionException) {
            throw e.exception
        }
    }

    /**
     * Starts the [bundle].
     *
     * A [BundleException] is thrown if the bundle fails to start.
     */
    fun startBundle(bundle: Bundle): Unit = try {
        // CorDapp code will call this method indirectly when creating transaction verification sandboxes. The use of
        // `doPrivileged` here prevents the limited permissions of the calling code (i.e. the CorDapp code) from 
        // causing this operation to be denied.
        doPrivileged(PrivilegedExceptionAction(bundle::start))
    } catch (e: PrivilegedActionException) {
        throw e.exception
    }

    /** Returns the bundle from which [klass] is loaded, or null if there is no such bundle. */
    fun getBundle(klass: Class<*>): Bundle? = FrameworkUtil.getBundle(klass) ?: try {
        // The lookup approach above does not work for the system bundle.
        if (loadClassFromSystemBundle(klass.name) === klass) systemBundle else null
    } catch (e: ClassNotFoundException) {
        null
    }

    /** Loads OSGi or java platform classes. */
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
}