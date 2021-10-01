package net.corda.kryoserialization

import net.corda.install.InstallService
import net.corda.packaging.Cpk
import net.corda.sandbox.SandboxCreationService
import net.corda.sandbox.SandboxGroup
import net.corda.v5.crypto.SecureHash
import org.junit.jupiter.api.fail
import java.io.InputStream
import java.net.URI
import java.security.DigestInputStream
import java.security.MessageDigest

class SandboxManagementService {
    companion object {

//        private val baseDirectory = Paths.get(
//            URI.create(System.getProperty("base.directory") ?: fail("base.directory property not found"))
//        ).toAbsolutePath()

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

    private val installService: InstallService = ServiceLocator.getInstallService()
    private val sandboxCreationService: SandboxCreationService = ServiceLocator.getSandboxCreationService()

    val cpk1: Cpk = loadCPK(resourceName = CPK_ONE)
    val cpk2: Cpk = loadCPK(resourceName = CPK_TWO)
    val group1: SandboxGroup = createSandboxGroupFor(cpk1)
    val group2: SandboxGroup = createSandboxGroupFor(cpk2)

//        val allBundles = FrameworkUtil.getBundle(this::class.java).bundleContext.bundles
//        val (publicBundles, privateBundles) = allBundles.partition { bundle ->
//            bundle.symbolicName in PLATFORM_PUBLIC_BUNDLE_NAMES
//        }
//        sandboxCreationService.createPublicSandbox(publicBundles, privateBundles)

    @Suppress("SameParameterValue")
    private fun loadCPK(resourceName: String): Cpk {
        val location = loadResource(resourceName)
        return location.toURL().openStream().buffered().use { source ->
            installService.loadCpk(hashOf(location, SHA256), source)
        }
    }

    private fun createSandboxGroupFor(vararg cpks: Cpk): SandboxGroup {
        return sandboxCreationService.createSandboxGroup(cpks.map(Cpk::cpkHash))
    }
}
