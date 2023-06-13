package net.corda.testing.driver.sandbox

import java.time.Duration
import java.util.LinkedList
import java.util.concurrent.TimeoutException
import net.corda.testing.driver.node.ServiceFactory
import org.osgi.framework.BundleContext

class ServiceFactoryImpl(private val bundleContext: BundleContext) : ServiceFactory {
    private val cleanups = LinkedList<AutoCloseable>()

    /**
     * Fetch and hold a reference to a service of class [serviceType].
     * Service objects are reference-counted, and so we must release
     * this reference when we've finished with it to allow the
     * service to be destroyed.
     */
    override fun <T : Any> getService(serviceType: Class<T>, filter: String?, timeout: Duration): T {
        var remainingMillis = timeout.toMillis().coerceAtLeast(0)
        while (true) {
            bundleContext.getServiceReferences(serviceType, filter).maxOrNull()?.let { ref ->
                val service = bundleContext.getService(ref)
                if (service != null) {
                    cleanups.addFirst { bundleContext.ungetService(ref) }
                    return service
                }
            }
            if (remainingMillis <= 0) {
                break
            }
            val waitMillis = remainingMillis.coerceAtMost(WAIT_MILLIS)
            Thread.sleep(waitMillis)
            remainingMillis -= waitMillis
        }
        val serviceDescription = serviceType.name + (filter?.let { f -> ", filter=$f" } ?: "")
        throw TimeoutException("Service $serviceDescription did not arrive in ${timeout.toMillis()} milliseconds")
    }

    override fun shutdown() {
        cleanups.forEach(AutoCloseable::close)
        cleanups.clear()
    }
}
