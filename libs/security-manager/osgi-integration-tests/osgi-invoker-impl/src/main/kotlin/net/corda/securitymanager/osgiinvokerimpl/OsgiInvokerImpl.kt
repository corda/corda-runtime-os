package net.corda.securitymanager.osgiinvokerimpl

import net.corda.securitymanager.osgiinvoker.OsgiInvoker
import org.osgi.framework.BundleContext
import org.osgi.framework.FrameworkUtil
import org.osgi.service.component.annotations.Component

// TODO - Rename - no longer just used to invoke OSGi permissions.
/** An implementation of the [OsgiInvoker] interface. */
@Component
@Suppress("unused")
class OsgiInvokerImpl: OsgiInvoker {
    private companion object {
        private val bundleContext: BundleContext = FrameworkUtil.getBundle(this::class.java).bundleContext
    }

    override fun performActionRequiringRuntimePermission() {
        System.getenv()
    }

    override fun performActionRequiringServiceGetPermission() {
        val serviceReference = bundleContext.getServiceReference(Any::class.java)
        bundleContext.getService(serviceReference)
    }

    override fun performActionRequiringServiceRegisterPermission() {
        bundleContext.registerService(Any::class.java, Any(), null)
    }
}