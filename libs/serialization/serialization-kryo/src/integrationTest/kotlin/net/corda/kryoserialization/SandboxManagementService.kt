package net.corda.kryoserialization

import net.corda.install.InstallService
import net.corda.packaging.CPK
import net.corda.sandbox.SandboxCreationService
import net.corda.sandbox.SandboxGroup
import net.corda.v5.crypto.SecureHash
import org.junit.jupiter.api.fail
import org.osgi.framework.FrameworkUtil
import java.io.InputStream
import java.net.URI
import java.security.DigestInputStream
import java.security.MessageDigest

class SandboxManagementService(
    private val installService: InstallService,
    private val sandboxCreationService: SandboxCreationService
) {
    companion object {
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
        val allBundles = FrameworkUtil.getBundle(this::class.java).bundleContext.bundles
        val (publicBundles, privateBundles) = allBundles.partition { bundle ->
            bundle.symbolicName in PLATFORM_PUBLIC_BUNDLE_NAMES
        }
        sandboxCreationService.createPublicSandbox(publicBundles, privateBundles)
    }

    val cpk1: CPK = loadCPK(resourceName = CPK_ONE)
    val cpk2: CPK = loadCPK(resourceName = CPK_TWO)
    val group1: SandboxGroup = createSandboxGroupFor(cpk1)
    val group2: SandboxGroup = createSandboxGroupFor(cpk2)

    private fun loadCPK(resourceName: String): CPK {
        val location = loadResource(resourceName)
        return location.toURL().openStream().buffered().use { source ->
            installService.loadCpk(hashOf(location, SHA256), source)
        }
    }

    private fun createSandboxGroupFor(vararg cpks: CPK): SandboxGroup {
        return sandboxCreationService.createSandboxGroup(cpks.map { it.metadata.hash })
    }
}
