package net.corda.testing.sandboxes.impl

import java.nio.file.Path
import java.util.Hashtable
import java.util.Collections.unmodifiableSet
import net.corda.install.InstallService
import net.corda.sandbox.SandboxCreationService
import net.corda.testing.sandboxes.SandboxSetup
import net.corda.testing.sandboxes.impl.InstallServiceImpl.Companion.BASE_DIRECTORY_KEY
import net.corda.testing.sandboxes.impl.InstallServiceImpl.Companion.TEST_BUNDLE_KEY
import org.osgi.framework.BundleContext
import org.osgi.framework.FrameworkUtil
import org.osgi.framework.ServiceReference
import org.osgi.service.cm.ConfigurationAdmin
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference

@Suppress("unused")
@Component
class SandboxSetupImpl @Activate constructor(
    @Reference
    private val configAdmin: ConfigurationAdmin,
    @Reference
    private val sandboxCreator: SandboxCreationService
) : SandboxSetup {
    companion object {
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
    }

    override fun configure(
        bundleContext: BundleContext,
        baseDirectory: Path,
        extraPublicBundleNames: Set<String>
    ) {
        configAdmin.getConfiguration(InstallServiceImpl::class.java.name)?.also { config ->
            val properties = Hashtable<String, Any?>()
            properties[BASE_DIRECTORY_KEY] = baseDirectory.toString()
            properties[TEST_BUNDLE_KEY] = bundleContext.bundle.location
            config.update(properties)
        }

        val platformPublicBundleNames = PLATFORM_PUBLIC_BUNDLE_NAMES + extraPublicBundleNames
        val (publicBundles, privateBundles) = bundleContext.bundles.partition { bundle ->
            bundle.symbolicName in platformPublicBundleNames
        }
        sandboxCreator.createPublicSandbox(publicBundles, privateBundles)
    }

    /**
     * Grab a reference to the current [InstallService] and shut it down.
     * We must ensure this happens before JUnit tries to remove the
     * temporary directory.
     */
    override fun shutdown() {
        val bundleContext = (FrameworkUtil.getBundle(this::class.java) ?: return).bundleContext
        bundleContext.getServiceReference(InstallService::class.java.name)?.also { ref ->
            @Suppress("unchecked_cast")
            bundleContext.getService(ref as ServiceReference<InstallService>)?.also { install ->
                try {
                    install.stop()
                } finally {
                    bundleContext.ungetService(ref)
                }
            }
        }
    }
}
