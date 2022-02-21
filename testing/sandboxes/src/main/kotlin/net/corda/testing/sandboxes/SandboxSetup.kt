package net.corda.testing.sandboxes

import java.nio.file.Path
import org.osgi.framework.BundleContext

interface SandboxSetup {
    fun configure(
        bundleContext: BundleContext,
        baseDirectory: Path
    )

    fun start()
    fun shutdown()

    fun <T> getService(serviceType: Class<T>, timeout: Long): T
}

inline fun <reified T> SandboxSetup.fetchService(timeout: Long): T {
    return getService(T::class.java, timeout)
}
