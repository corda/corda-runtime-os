package net.corda.sandboxhooks

import net.corda.install.InstallService
import net.corda.packaging.CPK
import net.corda.sandbox.Sandbox
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
import java.util.Dictionary
import java.util.Properties

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
    }

    init {
        configAdmin.getConfiguration(ConfigurationAdmin::class.java.name)?.also { config ->
            val properties = Properties()
            properties[BASE_DIRECTORY_KEY] = baseDirectory.toString()
            properties[BLACKLISTED_KEYS_KEY] = emptyList<String>()
            properties[PLATFORM_VERSION_KEY] = 999
            @Suppress("unchecked_cast")
            config.update(properties as Dictionary<String, Any>)
        }

        val allBundles = FrameworkUtil.getBundle(this::class.java).bundleContext.bundles
        val (publicBundles, privateBundles) = allBundles.partition { bundle ->
            bundle.symbolicName in PLATFORM_PUBLIC_BUNDLE_NAMES
        }
        sandboxCreationService.createPublicSandbox(publicBundles, privateBundles)
    }

    val cpk1 = loadCPK(resourceName = CPK_ONE)
    val cpk2 = loadCPK(resourceName = CPK_TWO)
    val cpk3 = loadCPK(resourceName = CPK_THREE)

    val group1 = createSandboxGroupFor(cpk1, cpk2)
    val group2 = createSandboxGroupFor(cpk3)

    val sandbox1 = group1.getSandbox(cpk1.metadata.id)
    val sandbox2 = group1.getSandbox(cpk2.metadata.id)
    val sandbox3 = group2.getSandbox(cpk3.metadata.id)

    /** Runs the flow with [className] in sandbox group [group] and casts the return value to [T]. */
    internal fun <T : Any> runFlow(className: String, group: SandboxGroup): T {
        val workflowClass = group.loadClassFromCordappBundle(className, Flow::class.java)
        @Suppress("unchecked_cast")
        return getServiceFor(Flow::class.java, workflowClass).call() as? T
            ?: fail("Workflow did not return the correct type.")
    }

    private fun loadCPK(resourceName: String): CPK {
        val location = loadResource(resourceName)
        return location.toURL().openStream().buffered().use { source ->
            installService.loadCpk(hashOf(location, SHA256), source)
        }
    }

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

    private fun createSandboxGroupFor(vararg cpks: CPK): SandboxGroup {
        return sandboxCreationService.createSandboxGroup(cpks.map { it.metadata.hash })
    }

    private fun <T, U : T> getServiceFor(serviceType: Class<T>, bundleClass: Class<U>): T {
        val context = FrameworkUtil.getBundle(bundleClass).bundleContext
        return context.getServiceReferences(serviceType, null)
            .map(context::getService)
            .filterIsInstance(bundleClass)
            .firstOrNull() ?: fail("No service for $serviceType.")
    }

    fun containsBundle(bundle: Bundle, sandbox: Sandbox): Boolean {
        val containsMethod = sandbox::class.java.getMethod("containsBundle", Bundle::class.java)
        return containsMethod.invoke(sandbox, bundle) as Boolean
    }

    fun containsBundle(bundle: Bundle, group: SandboxGroup): Boolean {
        return group.sandboxes.any { sandbox -> containsBundle(bundle, sandbox) }
    }
}
