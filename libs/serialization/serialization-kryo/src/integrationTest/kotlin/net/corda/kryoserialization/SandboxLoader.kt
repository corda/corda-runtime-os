package net.corda.kryoserialization

import net.corda.install.InstallService
import net.corda.packaging.Cpk
import net.corda.sandbox.CpkSandbox
import net.corda.sandbox.SandboxCreationService
import net.corda.sandbox.SandboxGroup
import net.corda.v5.application.flows.Flow
import net.corda.v5.crypto.SecureHash
import org.junit.jupiter.api.fail
import org.osgi.framework.Bundle
import org.osgi.framework.FrameworkUtil
import org.osgi.service.cm.ConfigurationAdmin
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import java.io.InputStream
import java.net.URI
import java.nio.file.Paths
import java.security.DigestInputStream
import java.security.MessageDigest
import java.util.*

@Component(service = [SandboxLoader::class])
class SandboxLoader @Activate constructor(
    @Reference
    configAdmin: ConfigurationAdmin,

    @Reference
    private val installService: InstallService,

    @Reference
    private val sandboxCreationService: SandboxCreationService
) {
    companion object {
        private val baseDirectory = Paths.get(
            URI.create(System.getProperty("base.directory") ?: fail("base.directory property not found"))
        ).toAbsolutePath()

        private fun loadResource(resourceName: String): URI {
            return (this::class.java.classLoader.getResource(resourceName)
                ?: fail("Failed to load $resourceName")).toURI()
        }

        @Suppress("SameParameterValue")
        private fun hashOf(location: URI, algorithm: String): SecureHash {
            val digest = MessageDigest.getInstance(algorithm)
            DigestInputStream(location.toURL().openStream(), digest).use(::consume)
            return SecureHash(algorithm, digest.digest())
        }

        private fun consume(input: InputStream) {
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (input.read(buffer) >= 0) {
                continue
            }
        }
    }

    val cpk1: Cpk

    val group1: SandboxGroup

    init {
        val privateBundleNames = FrameworkUtil.getBundle(this::class.java).bundleContext.bundles.filter { bundle ->
            bundle.symbolicName !in PLATFORM_PUBLIC_BUNDLE_NAMES
        }.map(Bundle::getSymbolicName)

        configAdmin.getConfiguration(ConfigurationAdmin::class.java.name)?.also { config ->
            val properties = Properties()
            properties[BASE_DIRECTORY_KEY] = baseDirectory.toString()
            properties[BLACKLISTED_KEYS_KEY] = emptyList<String>()
            properties[PLATFORM_VERSION_KEY] = 999
            properties[PLATFORM_SANDBOX_PUBLIC_BUNDLES_KEY] = PLATFORM_PUBLIC_BUNDLE_NAMES
            properties[PLATFORM_SANDBOX_PRIVATE_BUNDLES_KEY] = privateBundleNames
            @Suppress("unchecked_cast")
            config.update(properties as Dictionary<String, Any>)
        }

        cpk1 = loadCPK(resourceName = CPK_ONE)
        group1 = createSandboxGroupFor(cpk1)
    }

    /** Runs the flow with [className] in sandbox group [group] and casts the return value to [T]. */
    internal fun <T: Any> runFlow(className: String, group: SandboxGroup): T {
        val workflowClass = group.loadClassFromCordappBundle(className, Flow::class.java)
        @Suppress("unchecked_cast")
        return getServiceFor(Flow::class.java, workflowClass).call() as? T
            ?: fail("Workflow did not return the correct type.")
    }

    private fun loadCPK(resourceName: String): Cpk {
        val location = loadResource(resourceName)
        return location.toURL().openStream().buffered().use { source ->
            installService.loadCpk(hashOf(location, SHA256), source)
        }
    }

    private fun createSandboxGroupFor(vararg cpks: Cpk): SandboxGroup {
        return sandboxCreationService.createSandboxGroup(cpks.map(Cpk::cpkHash))
    }

    private fun <T, U : T> getServiceFor(serviceType: Class<T>, bundleClass: Class<U>): T {
        val context = FrameworkUtil.getBundle(bundleClass).bundleContext
        return context.getServiceReferences(serviceType, null)
            .map(context::getService)
            .filterIsInstance(bundleClass)
            .firstOrNull() ?: fail("No service for $bundleClass")
    }

    @Suppress("SameParameterValue")
    private fun getBundle(className: String, sandbox: CpkSandbox): Bundle {
        return FrameworkUtil.getBundle(sandbox.loadClassFromCordappBundle(className))
    }

}
