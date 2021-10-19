package net.corda.securitymanager.invokerimpl

import net.corda.securitymanager.invoker.Invoker
import org.osgi.framework.BundleContext
import org.osgi.framework.FrameworkUtil
import org.osgi.service.component.annotations.Component

/** An implementation of the [Invoker] interface. */
@Component
@Suppress("unused")
class InvokerImpl: Invoker {
    private companion object {
        private val bundleContext: BundleContext = FrameworkUtil.getBundle(this::class.java).bundleContext
    }

    override fun performActionRequiringRuntimePermission() {
        // Triggers a security check against the `getenv.{variable name}` permission target.
        System.getenv()
    }

    override fun performActionRequiringServiceGetPermission() {
        // Triggers a security check against the OSGi `ServicePermission.GET` action.
        val serviceReference = bundleContext.getServiceReference(Any::class.java)
        bundleContext.getService(serviceReference)
    }

    override fun performActionRequiringServiceRegisterPermission() {
        // Triggers a security check against the OSGi `ServicePermission.REGISTER` action.
        bundleContext.registerService(Any::class.java, Any(), null)
    }
}