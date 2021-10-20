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

    override fun performActionRequiringGetEnvRuntimePermission() {
        System.getenv("ENV_VAR")
    }

    override fun performActionRequiringGetProtectionDomainRuntimePermission() {
        Any::class.java.protectionDomain
    }

    override fun performActionRequiringServiceGetPermission() {
        val serviceReference = bundleContext.getServiceReference(Any::class.java)
        bundleContext.getService(serviceReference)
    }

    override fun performActionRequiringServiceRegisterPermission() {
        bundleContext.registerService(Any::class.java, Any(), null)
    }
}