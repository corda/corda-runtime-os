package net.corda.securitymanager.osgiinvokerimpl

import net.corda.securitymanager.osgiinvoker.OsgiInvoker
import org.osgi.framework.BundleContext
import org.osgi.framework.Constants.SYSTEM_BUNDLE_ID
import org.osgi.framework.FrameworkUtil
import org.osgi.framework.SynchronousBundleListener
import org.osgi.framework.wiring.FrameworkWiring
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.condpermadmin.ConditionalPermissionAdmin

/** An implementation of the [OsgiInvoker] interface. */
@Component
@Suppress("unused")
class OsgiInvokerImpl @Activate constructor(private val context: BundleContext): OsgiInvoker {
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
        val serviceReference = context.getServiceReference(ConditionalPermissionAdmin::class.java)
        context.getService(serviceReference)
    }
}