package test

import net.corda.install.InstallService
import net.corda.packaging.Cpk
import net.corda.sandbox.SandboxCreationService
import net.corda.v5.crypto.SecureHash
import org.junit.jupiter.api.fail
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

@Component(service = [SandboxLoader::class])
class SandboxLoader @Activate constructor(
    @Reference
    val sandboxCreationService: SandboxCreationService,
    @Reference
    private val configAdmin: ConfigurationAdmin,
    @Reference
    private val installService: InstallService
) {
    init {
        setConfigAdminProperties()
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
