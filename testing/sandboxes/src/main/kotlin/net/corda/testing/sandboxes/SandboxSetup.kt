package net.corda.testing.sandboxes

import java.nio.file.Path
import org.osgi.framework.BundleContext

interface SandboxSetup {
    companion object {
        const val SANDBOX_SERVICE_RANKING = Int.MAX_VALUE / 2
    }

    fun configure(bundleContext: BundleContext, baseDirectory: Path)

    fun start()
    fun shutdown()

    fun <T> getService(serviceType: Class<T>, filter: String?, timeout: Long): T
    fun <T> getService(serviceType: Class<T>, timeout: Long): T = getService(serviceType, null, timeout)

    fun withCleanup(closeable: AutoCloseable)
}

inline fun <reified T> SandboxSetup.fetchService(timeout: Long): T {
    return getService(T::class.java, timeout)
}

inline fun <reified T> SandboxSetup.fetchService(filter: String?, timeout: Long): T {
    return getService(T::class.java, filter, timeout)
}
