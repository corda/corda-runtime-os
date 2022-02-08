package net.corda.flow.sandbox

import java.nio.file.Path
import java.util.Hashtable
import java.util.Collections.unmodifiableList
import net.corda.sandbox.SandboxCreationService
import org.osgi.framework.BundleContext
import org.osgi.service.cm.ConfigurationAdmin
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference

@Component(service = [ SandboxSetup::class ])
class SandboxSetup @Activate constructor(
    @Reference
    private val configAdmin: ConfigurationAdmin,
    @Reference
    private val sandboxCreator: SandboxCreationService,
    private val bundleContext: BundleContext
) {
    companion object {
        const val BASE_DIRECTORY_KEY = "baseDirectory"

        // The names of the bundles to place as public bundles in the sandbox service's platform sandbox.
        private val PLATFORM_PUBLIC_BUNDLE_NAMES: List<String> = unmodifiableList(listOf(
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

    fun configure(baseDirectory: Path) {
        configAdmin.getConfiguration(ConfigurationAdmin::class.java.name)?.also { config ->
            val properties = Hashtable<String, Any?>()
            properties[BASE_DIRECTORY_KEY] = baseDirectory.toString()
            config.update(properties)
        }

        // We should be able to replace this by "starting" SandboxGroupContextComponent.
        val (publicBundles, privateBundles) = bundleContext.bundles.partition { bundle ->
            bundle.symbolicName in PLATFORM_PUBLIC_BUNDLE_NAMES
        }
        sandboxCreator.createPublicSandbox(publicBundles, privateBundles)
    }
}
