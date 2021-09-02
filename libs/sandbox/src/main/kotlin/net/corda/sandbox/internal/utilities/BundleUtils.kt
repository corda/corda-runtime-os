package net.corda.sandbox.internal.utilities

import net.corda.sandbox.SandboxService
import org.osgi.framework.Bundle
import org.osgi.framework.BundleContext
import org.osgi.framework.BundleException
import org.osgi.framework.FrameworkUtil
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import java.net.URI
import java.security.AccessController.doPrivileged
import java.security.PrivilegedActionException
import java.security.PrivilegedExceptionAction

/** Handles bundle operations for the [SandboxService]. */
@Component(service = [BundleUtils::class])
internal class BundleUtils @Activate constructor(private val bundleContext: BundleContext) {
    /**
     * Installs the contents of the [uri] as a bundle, using the [location] provided.
     *
     * A [BundleException] is thrown if the bundle fails to install.
     */
    fun installAsBundle(location: String, uri: URI): Bundle = uri.toURL().openStream().use { inputStream ->
        // CorDapp code will call this method indirectly when creating transaction verification sandboxes. The use of
        // `doPrivileged` here prevents the limited permissions of the calling code (i.e. the CorDapp code) from
        // causing this operation to be denied.
        try {
            doPrivileged(PrivilegedExceptionAction {
                bundleContext.installBundle(location, inputStream)
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
    fun getBundle(klass: Class<*>): Bundle? = FrameworkUtil.getBundle(klass)

    /** Returns the list of all installed bundles. */
    val allBundles get() = bundleContext.bundles.toList()
}