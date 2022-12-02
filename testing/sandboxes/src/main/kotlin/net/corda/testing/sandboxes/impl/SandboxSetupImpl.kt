package net.corda.testing.sandboxes.impl

import net.corda.cpk.read.CpkReadService
import net.corda.sandbox.SandboxCreationService
import net.corda.testing.sandboxes.CpiLoader
import net.corda.testing.sandboxes.SandboxSetup
import net.corda.testing.sandboxes.impl.SandboxSetupImpl.Companion.INSTALLER_NAME
import net.corda.v5.base.util.loggerFor
import org.osgi.framework.BundleContext
import org.osgi.service.cm.ConfigurationAdmin
import org.osgi.service.component.ComponentContext
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Deactivate
import org.osgi.service.component.annotations.Reference
import org.osgi.service.component.annotations.ReferenceCardinality.OPTIONAL
import org.osgi.service.component.annotations.ReferencePolicy.DYNAMIC
import java.nio.file.Path
import java.util.Collections.unmodifiableSet
import java.util.Deque
import java.util.Hashtable
import java.util.LinkedList
import java.util.concurrent.TimeoutException

@Suppress("unused")
@Component(
    reference = [ Reference(
        name = INSTALLER_NAME,
        service = CpkReadService::class,
        cardinality = OPTIONAL,
        policy = DYNAMIC
    )]
)
class SandboxSetupImpl @Activate constructor(
    @Reference
    private val configAdmin: ConfigurationAdmin,
    @Reference
    private val sandboxCreator: SandboxCreationService,
    private val componentContext: ComponentContext
) : SandboxSetup {
    companion object {
        const val INSTALLER_NAME = "installer"
        private const val WAIT_MILLIS = 100L

        // The names of the bundles to place as public bundles in the sandbox service's platform sandbox.
        private val PLATFORM_PUBLIC_BUNDLE_NAMES: Set<String> = unmodifiableSet(setOf(
            "co.paralleluniverse.quasar-core.framework.extension",
            "com.esotericsoftware.reflectasm",
            "javax.persistence-api",
            "jcl.over.slf4j",
            "net.corda.application",
            "net.corda.base",
            "net.corda.crypto",
            "net.corda.crypto-extensions",
            "net.corda.kotlin-stdlib-jdk7.osgi-bundle",
            "net.corda.kotlin-stdlib-jdk8.osgi-bundle",
            "net.corda.ledger-consensual",
            "net.corda.ledger-utxo",
            "net.corda.membership",
            "net.corda.persistence",
            "net.corda.serialization",
            "net.corda.test-api",
            "org.apache.aries.spifly.dynamic.framework.extension",
            "org.apache.felix.framework",
            "org.apache.felix.scr",
            "org.hibernate.orm.core",
            "org.jetbrains.kotlin.osgi-bundle",
            "slf4j.api"
        ))

        private val logger = loggerFor<SandboxSetup>()
    }

    private val cleanups: Deque<AutoCloseable> = LinkedList()

    override fun configure(
        bundleContext: BundleContext,
        baseDirectory: Path
    ) {
        val testBundle = bundleContext.bundle
        logger.info("Configuring sandboxes for [{}]", testBundle.symbolicName)

        configAdmin.getConfiguration(CpiLoader.COMPONENT_NAME)?.also { config ->
            val properties = Hashtable<String, Any?>()
            properties[CpiLoader.BASE_DIRECTORY_KEY] = baseDirectory.toString()
            properties[CpiLoader.TEST_BUNDLE_KEY] = testBundle.location
            config.update(properties)
        }

        val (publicBundles, privateBundles) = bundleContext.bundles.partition { bundle ->
            bundle.symbolicName in PLATFORM_PUBLIC_BUNDLE_NAMES
        }
        sandboxCreator.createPublicSandbox(publicBundles, privateBundles)
    }

    /**
     * Enables the InstallService component, allowing
     * the framework to create new instances of it.
     */
    override fun start() {
        componentContext.enableComponent(CpiLoader.COMPONENT_NAME)
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

        /**
         * Deactivate the [CpkReadService] and then wait
         * for the framework to unregister it.
         */
        with(componentContext) {
            disableComponent(CpiLoader.COMPONENT_NAME)
            while (locateService<CpkReadService>(INSTALLER_NAME) != null) {
                Thread.sleep(WAIT_MILLIS)
            }
        }

        logger.info("Shutdown complete")
    }

    /**
     * Fetch and hold a reference to a service of class [serviceType].
     * Service objects are reference-counted, and so we must release
     * this reference when we've finished with it to allow the
     * service to be destroyed.
     */
    override fun <T> getService(serviceType: Class<T>, filter: String?, timeout: Long): T {
        val bundleContext = componentContext.bundleContext
        var remainingMillis = timeout.coerceAtLeast(0)
        while (true) {
            bundleContext.getServiceReferences(serviceType, filter).maxOrNull()?.let { ref ->
                val service = bundleContext.getService(ref)
                if (service != null) {
                    withCleanup { bundleContext.ungetService(ref) }
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
        throw TimeoutException("Service $serviceDescription did not arrive in $timeout milliseconds")
    }

    override fun withCleanup(closeable: AutoCloseable) {
        cleanups.addFirst(closeable)
    }
}
