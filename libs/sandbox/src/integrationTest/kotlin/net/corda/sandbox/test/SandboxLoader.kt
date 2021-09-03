package net.corda.sandbox.test

import net.corda.install.InstallService
import net.corda.packaging.Cpk
import net.corda.sandbox.Sandbox
import net.corda.sandbox.SandboxGroup
import net.corda.sandbox.SandboxService
import net.corda.v5.crypto.SecureHash
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions.assertNotEquals
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
    private val sandboxService: SandboxService
) {
    companion object {
        const val LIBRARY_SYMBOLIC_NAME = "com.example.sandbox.sandbox-cpk-library"
        const val QUERY_CLASS = "com.example.sandbox.library.SandboxQuery"

        private const val SHA256 = "SHA-256"

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
    val cpk2: Cpk
    val cpk3: Cpk

    val group1: SandboxGroup
    val group2: SandboxGroup

    init {
        configAdmin.getConfiguration(ConfigurationAdmin::class.java.name)?.also { config ->
            val properties = Properties()
            properties["baseDirectory"] = baseDirectory.toString()
            properties["blacklistedKeys"] = emptyList<String>()
            properties["platformVersion"] = 999
            @Suppress("unchecked_cast")
            config.update(properties as Dictionary<String, Any>)
        }

        cpk1 = loadCPK(resourceName = "sandbox-cpk-one-cordapp.cpk")
        cpk2 = loadCPK(resourceName = "sandbox-cpk-two-cordapp.cpk")
        cpk3 = loadCPK(resourceName = "sandbox-cpk-three-cordapp.cpk")

        group1 = createSandboxGroupFor(cpk1, cpk2)
        assertThat(group1.sandboxes).hasSize(2)

        group2 = createSandboxGroupFor(cpk3)
        assertThat(group2.sandboxes).hasSize(1)

        val sandbox1 = group1.getSandbox(cpk1.id)
        val sandbox2 = group1.getSandbox(cpk2.id)
        val sandbox3 = group2.getSandbox(cpk3.id)

        // Verify sandbox1 and sandbox2 can see each other.
        assertMutuallyVisible(sandbox1, sandbox2)

        // Verify sandbox1 and sandbox3 cannot see each other.
        assertMutuallyInvisible(sandbox1, sandbox3)

        // Verify sandbox2 and sandbox3 cannot see each other.
        assertMutuallyInvisible(sandbox2, sandbox3)

        val library1 = getBundle(QUERY_CLASS, sandbox1)
        val library2 = getBundle(QUERY_CLASS, sandbox2)
        val library3 = getBundle(QUERY_CLASS, sandbox3)

        // Verify these bundles are all distinct,
        // but still share the same symbolic name.
        assertDistinctDuplicates(library1, library2)
        assertDistinctDuplicates(library1, library3)
        assertDistinctDuplicates(library2, library3)
    }

    private fun loadCPK(resourceName: String): Cpk {
        val location = loadResource(resourceName)
        return location.toURL().openStream().buffered().use { source ->
            installService.loadCpk(hashOf(location, SHA256), source)
        }
    }

    private fun assertMutuallyVisible(sandboxA: Sandbox, sandboxB: Sandbox) {
        assertNotEquals(sandboxA, sandboxB)
        assertThat(hasVisibility(sandboxA, sandboxB)).isTrue
        assertThat(hasVisibility(sandboxB, sandboxA)).isTrue
    }

    private fun assertMutuallyInvisible(sandboxA: Sandbox, sandboxB: Sandbox) {
        assertThat(hasVisibility(sandboxA, sandboxB)).isFalse
        assertThat(hasVisibility(sandboxB, sandboxA)).isFalse
    }

    private fun assertDistinctDuplicates(bundle1: Bundle, bundle2: Bundle) {
        assertThat(bundle1.symbolicName).isEqualTo(bundle2.symbolicName)
        assertThat(bundle1.bundleId).isNotEqualTo(bundle2.bundleId)
    }

    private fun createSandboxGroupFor(vararg cpks: Cpk): SandboxGroup {
        return sandboxService.createSandboxes(cpks.map(Cpk::cpkHash))
    }

    fun <T, U : T> getServiceFor(serviceType: Class<T>, bundleClass: Class<U>): T {
        val context = FrameworkUtil.getBundle(bundleClass).bundleContext
        return context.getServiceReferences(serviceType, null)
            .map(context::getService)
            .filterIsInstance(bundleClass)
            .firstOrNull() ?: fail("No service for $bundleClass")
    }

    private fun hasVisibility(sandbox1: Sandbox, sandbox2: Sandbox): Boolean {
        val hasVisibilityMethod = sandbox1::class.java.getMethod("hasVisibility", Sandbox::class.java)
        return hasVisibilityMethod.invoke(sandbox1, sandbox2) as Boolean
    }

    @Suppress("SameParameterValue")
    private fun getBundle(className: String, sandbox: Sandbox): Bundle {
        return getBundle(sandbox.loadClass(className), sandbox)
    }

    private fun getBundle(clazz: Class<*>, sandbox: Sandbox): Bundle {
        val getBundleMethod = sandbox::class.java.getMethod("getBundle", Class::class.java)
        return getBundleMethod.invoke(sandbox, clazz) as Bundle
    }

    fun containsBundle(bundle: Bundle, sandbox: Sandbox): Boolean {
        val containsMethod = sandbox::class.java.getMethod("containsBundle", Bundle::class.java)
        return containsMethod.invoke(sandbox, bundle) as Boolean
    }

    fun containsBundle(bundle: Bundle, group: SandboxGroup): Boolean {
        return group.sandboxes.any { sandbox -> containsBundle(bundle, sandbox) }
    }

    fun containsClass(clazz: Class<*>, group: SandboxGroup): Boolean {
        val bundle = FrameworkUtil.getBundle(clazz) ?: return false
        return containsBundle(bundle, group)
    }
}
