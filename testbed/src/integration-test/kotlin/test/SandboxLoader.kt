package test

import net.corda.install.InstallService
import net.corda.packaging.Cpk
import net.corda.sandbox.SandboxAdminService
import net.corda.sandbox.SandboxCreationService
import net.corda.v5.crypto.SecureHash
import org.junit.jupiter.api.fail
import org.osgi.framework.FrameworkUtil
import org.osgi.service.cm.ConfigurationAdmin
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import java.net.URI
import java.net.URL
import java.nio.file.Paths
import java.security.DigestInputStream
import java.security.MessageDigest
import java.util.Dictionary
import java.util.Properties

private const val CPK_FILENAME = "testbed-cpk-cordapp.cpk"
private const val BASE_DIR = "base.directory"
private const val SHA_256 = "SHA-256"
private const val BASE_DIR_PROP = "baseDirectory"
private const val PLATFORM_VERSION_PROP = "platformVersion"
private val PLATFORM_PUBLIC_BUNDLE_NAMES = listOf(
    "javax.persistence-api",
    "jcl.over.slf4j",
    "net.corda.application",
    "net.corda.base",
    "net.corda.crypto-api",
    "net.corda.flows",
    "net.corda.kotlin-stdlib-jdk7.osgi-bundle",
    "net.corda.kotlin-stdlib-jdk8.osgi-bundle",
    "net.corda.ledger",
    "net.corda.legacy-api",
    "net.corda.persistence",
    "net.corda.serialization",
    "org.apache.aries.spifly.dynamic.bundle",
    "org.apache.felix.framework",
    "org.apache.felix.scr",
    "org.hibernate.orm.core",
    "org.jetbrains.kotlin.osgi-bundle",
    "slf4j.api"
)

@Component(service = [SandboxLoader::class])
class SandboxLoader @Activate constructor(
    @Reference
    val sandboxCreationService: SandboxCreationService,
    @Reference
    val sandboxAdminService: SandboxAdminService,
    @Reference
    private val configAdmin: ConfigurationAdmin,
    @Reference
    private val installService: InstallService
) {
    init {
        setConfigAdminProperties()
        createPublicSandbox()
    }

    val cpk = loadCPK(CPK_FILENAME)

    private fun setConfigAdminProperties() {
        val baseDirectory = Paths.get(URI.create(System.getProperty(BASE_DIR))).toAbsolutePath().toString()

        configAdmin.getConfiguration(ConfigurationAdmin::class.java.name)
            ?.also { config ->
                val properties = Properties()
                properties[BASE_DIR_PROP] = baseDirectory
                properties[PLATFORM_VERSION_PROP] = 999
                @Suppress("unchecked_cast")
                config.update(properties as Dictionary<String, Any>)
            }
    }

    private fun createPublicSandbox() {
        val allBundles = FrameworkUtil.getBundle(this::class.java).bundleContext.bundles
        val (publicBundles, privateBundles) = allBundles.partition { bundle ->
            bundle.symbolicName in PLATFORM_PUBLIC_BUNDLE_NAMES
        }
        sandboxCreationService.createPublicSandbox(publicBundles, privateBundles)
    }

    @Suppress("SameParameterValue")
    private fun loadCPK(cpkFilename: String): Cpk {
        val cpkUri = this::class.java.classLoader.getResource(cpkFilename)
            ?: fail("Failed to load $cpkFilename.")
        val cpkUriHash = sha256HashOf(cpkUri)
        return cpkUri.openStream().buffered().use { source -> installService.loadCpk(cpkUriHash, source) }
    }

    private fun sha256HashOf(location: URL): SecureHash {
        val digest = MessageDigest.getInstance(SHA_256)
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        DigestInputStream(location.openStream(), digest).use { inputStream ->
            while (inputStream.read(buffer) >= 0) {
                continue
            }
        }
        return SecureHash(SHA_256, digest.digest())
    }
}
