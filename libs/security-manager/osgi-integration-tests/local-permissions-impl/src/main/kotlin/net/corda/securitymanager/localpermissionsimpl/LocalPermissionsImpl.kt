package net.corda.securitymanager.localpermissionsimpl

import net.corda.securitymanager.localpermissions.LocalPermissions
import org.osgi.framework.BundleContext
import org.osgi.framework.Constants.SYSTEM_BUNDLE_ID
import org.osgi.framework.FrameworkUtil
import org.osgi.framework.SynchronousBundleListener
import org.osgi.framework.wiring.FrameworkWiring
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.condpermadmin.ConditionalPermissionAdmin
import java.io.File

/**
 * An implementation of the [LocalPermissions] interface.
 *
 * This bundle contains a local permissions file (see `resources/permissions.perm`) that denies it any permissions
 * except a handful of `PackagePermission`s, and two `AdminPermission`s ("context" and "lifecycle").
 *
 * Note that of these, "lifecycle" is still denied by the `CordaSecurityManager`.
 */
@Component
@Suppress("unused")
class LocalPermissionsImpl @Activate constructor(private val context: BundleContext): LocalPermissions {
    companion object {
        private val bundle = FrameworkUtil.getBundle(this::class.java)
    }
    
    override fun getBundleContext() {
        bundle.bundleContext
    }

    override fun startBundle() {
        bundle.start()
    }

    override fun installBundle() {
        context.installBundle(bundle.location)
    }

    override fun addListener() {
        context.addBundleListener(SynchronousBundleListener { })
    }

    override fun loadClass() {
        bundle.loadClass(this::class.java.name)
    }

    override fun getLocation() {
        bundle.location
    }

    override fun refreshBundles() {
        val frameworkWiring = context.getBundle(SYSTEM_BUNDLE_ID).adapt(FrameworkWiring::class.java)
        frameworkWiring.refreshBundles(null)
    }

    override fun adaptBundle() {
        bundle.adapt(BundleContext::class.java)
    }

    override fun getService() {
        val serviceReference = context.getServiceReference(ConditionalPermissionAdmin::class.java)!!
        context.getService(serviceReference)
    }

    override fun readFile() {
        File("").readLines()
    }
}