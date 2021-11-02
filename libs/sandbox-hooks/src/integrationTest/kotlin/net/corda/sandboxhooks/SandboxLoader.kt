package net.corda.sandboxhooks

import net.corda.install.InstallService
import net.corda.packaging.CPK
import net.corda.sandbox.SandboxContextService
import net.corda.sandbox.SandboxCreationService
import net.corda.v5.crypto.SecureHash
import org.junit.jupiter.api.fail
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
import java.util.Hashtable

@Component(service = [SandboxLoader::class])
class SandboxLoader @Activate constructor(
    @Reference
    configAdmin: ConfigurationAdmin,
    @Reference
    private val installService: InstallService,
    @Reference
    private val sandboxCreationService: SandboxCreationService,
    @Reference
    val sandboxContextService: SandboxContextService
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

    init {
        configAdmin.getConfiguration(ConfigurationAdmin::class.java.name)?.also { config ->
            val properties = Hashtable<String, Any>()
            properties[BASE_DIRECTORY_KEY] = baseDirectory.toString()
            properties[BLACKLISTED_KEYS_KEY] = emptyList<String>()
            properties[PLATFORM_VERSION_KEY] = 999
            config.update(properties)
        }

        val allBundles = FrameworkUtil.getBundle(this::class.java).bundleContext.bundles
        val (publicBundles, privateBundles) = allBundles.partition { bundle ->
            bundle.symbolicName in PLATFORM_PUBLIC_BUNDLE_NAMES
        }
        sandboxCreationService.createPublicSandbox(publicBundles, privateBundles)
    }

    val cpk1 = loadCPK(resourceName = CPK_ONE)
    val cpk2 = loadCPK(resourceName = CPK_TWO)
    private val cpk3 = loadCPK(resourceName = CPK_THREE)

    val group1 = createSandboxGroupFor(cpk1, cpk2)
    val group2 = createSandboxGroupFor(cpk3)

    private fun loadCPK(resourceName: String): CPK {
        val location = loadResource(resourceName)
        return location.toURL().openStream().buffered().use { source ->
            installService.loadCpk(hashOf(location, SHA256), source)
        }
    }

    private fun createSandboxGroupFor(vararg cpks: CPK) =
        sandboxCreationService.createSandboxGroup(cpks.map { cpk -> cpk.metadata.hash })
}