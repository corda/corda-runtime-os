package net.corda.testing.sandboxes.impl

import java.nio.file.Path
import java.util.Hashtable
import java.util.Collections.unmodifiableSet
import java.util.concurrent.TimeoutException
import net.corda.sandbox.SandboxCreationService
import net.corda.testing.sandboxes.SandboxSetup
import net.corda.testing.sandboxes.impl.InstallServiceImpl.Companion.BASE_DIRECTORY_KEY
import net.corda.testing.sandboxes.impl.InstallServiceImpl.Companion.TEST_BUNDLE_KEY
import org.osgi.framework.BundleContext
import org.osgi.service.cm.ConfigurationAdmin
import org.osgi.service.component.ComponentContext
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Deactivate
import org.osgi.service.component.annotations.Reference
import org.slf4j.LoggerFactory

@Suppress("unused")
@Component
class SandboxSetupImpl @Activate constructor(
    @Reference
    private val configAdmin: ConfigurationAdmin,
    @Reference
    private val sandboxCreator: SandboxCreationService,
    private val componentContext: ComponentContext
) : SandboxSetup {
    companion object {
        private const val WAIT_MILLIS = 100L

        // The names of the bundles to place as public bundles in the sandbox service's platform sandbox.
        private val PLATFORM_PUBLIC_BUNDLE_NAMES: Set<String> = unmodifiableSet(setOf(
            "javax.persistence-api",
            "jcl.over.slf4j",
            "net.corda.application",
            "net.corda.base",
            "net.corda.cipher-suite",
            "net.corda.crypto",
            "net.corda.kotlin-stdlib-jdk7.osgi-bundle",
            "net.corda.kotlin-stdlib-jdk8.osgi-bundle",
            "net.corda.persistence",
            "net.corda.serialization",
            "org.apache.aries.spifly.dynamic.bundle",
            "org.apache.felix.framework",
            "org.apache.felix.scr",
            "org.hibernate.orm.core",
            "org.jetbrains.kotlin.osgi-bundle",
            "slf4j.api"
        ))

        private val logger = LoggerFactory.getLogger(SandboxSetup::class.java)
    }

    private val cleanups = mutableListOf<AutoCloseable>()

    override fun configure(
        bundleContext: BundleContext,
        baseDirectory: Path,
        extraPublicBundleNames: Set<String>
    ) {
        val testBundle = bundleContext.bundle
        logger.info("Configuring sandboxes for [{}]", testBundle.symbolicName)

        configAdmin.getConfiguration(InstallServiceImpl::class.java.name)?.also { config ->
            val properties = Hashtable<String, Any?>()
            properties[BASE_DIRECTORY_KEY] = baseDirectory.toString()
            properties[TEST_BUNDLE_KEY] = testBundle.location
            config.update(properties)
        }

        val platformPublicBundleNames = PLATFORM_PUBLIC_BUNDLE_NAMES + extraPublicBundleNames
        val (publicBundles, privateBundles) = bundleContext.bundles.partition { bundle ->
            bundle.symbolicName in platformPublicBundleNames
        }
        sandboxCreator.createPublicSandbox(publicBundles, privateBundles)
    }

    /**
     * Enables the InstallService component, allowing
     * the framework to create new instances of it.
     */
    override fun start() {
        componentContext.enableComponent(InstallServiceImpl::class.java.name)
    }

    /**
     * Disables the InstallService component to unload all CPIs.
     * We must ensure this happens before JUnit tries to remove the
     * temporary directory.
     */
    @Deactivate
    override fun shutdown() {
        synchronized(this) {
            cleanups.forEach(AutoCloseable::close)
            cleanups.clear()
        }
        componentContext.disableComponent(InstallServiceImpl::class.java.name)
        logger.info("Shutdown complete")
    }

    /**
     * Fetch and hold a reference to a service of class [serviceType].
     * Service objects are reference-counted, and so we must release
     * this reference when we've finished with it to allow the
     * service to be destroyed.
     */
    override fun <T> getService(serviceType: Class<T>, timeout: Long): T {
        val bundleContext = componentContext.bundleContext
        var remainingMillis = timeout.coerceAtLeast(0)
        while (true) {
            bundleContext.getServiceReference(serviceType)?.let { ref ->
                return bundleContext.getService(ref).also {
                    cleanups.add(AutoCloseable { bundleContext.ungetService(ref) })
                }
            }
            if (remainingMillis <= 0) {
                break
            }
            val waitMillis = remainingMillis.coerceAtMost(WAIT_MILLIS)
            Thread.sleep(waitMillis)
            remainingMillis -= waitMillis
        }
        throw TimeoutException("Service $serviceType did not arrive in $timeout milliseconds")
    }
}
