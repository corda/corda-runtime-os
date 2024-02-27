package net.corda.rest.ssl.impl

import net.corda.libs.configuration.SmartConfig
import net.corda.rest.ssl.impl.SslCertReadServiceImpl.Companion.KEYSTORE_NAME
import net.corda.rest.ssl.impl.SslCertReadServiceImpl.Companion.KEYSTORE_TYPE
import net.corda.rest.ssl.impl.SslCertReadServiceImpl.Companion.PASSWORD
import net.corda.rest.ssl.impl.SslCertReadServiceImpl.Companion.TLS_ENTRY
import net.corda.schema.configuration.BootConfig.BOOT_REST_TLS_CA_CRT_PATH
import net.corda.schema.configuration.BootConfig.BOOT_REST_TLS_CRT_PATH
import net.corda.schema.configuration.BootConfig.BOOT_REST_TLS_KEYSTORE_FILE_PATH
import net.corda.schema.configuration.BootConfig.BOOT_REST_TLS_KEYSTORE_PASSWORD
import net.corda.schema.configuration.BootConfig.BOOT_REST_TLS_KEY_PATH
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.security.KeyStore
import kotlin.io.path.inputStream
import kotlin.io.path.outputStream

class SslCertReadServiceImplTest {

    @TempDir
    lateinit var resourcesDir: Path

    private var tempDirectoryPath: Path? = null

    private val service = SslCertReadServiceImpl(
        createDirectory = {
            Files.createTempDirectory("rest-ssl").also {
                this.tempDirectoryPath = it
            }
        }
    )

    @AfterEach
    fun afterEach() {
        tempDirectoryPath?.let { File(it.toUri()).deleteRecursively() }
    }

    @Test
    fun `creates a keystore in tmp directory`() {
        service.getOrCreateKeyStoreInfo(mock())
        Assertions.assertTrue(
            File(
                Path.of(tempDirectoryPath.toString(), SslCertReadServiceImpl.KEYSTORE_NAME).toUri()
            ).exists()
        )
    }

    @Test
    fun `create returns path and password of keystore`() {
        val keyStoreInfo = service.getOrCreateKeyStoreInfo(mock())
        Assertions.assertEquals(
            Path.of(tempDirectoryPath.toString(), SslCertReadServiceImpl.KEYSTORE_NAME),
            keyStoreInfo.path
        )
        Assertions.assertEquals(SslCertReadServiceImpl.PASSWORD, keyStoreInfo.password)
    }

    @Test
    fun `create returns path and password of keystore when provided by the config`() {
        val configMock = mock<SmartConfig>()
        val keyStorePath = "/foo/bar/keystore.file"
        val password = "myPassword"
        whenever(configMock.hasPath(BOOT_REST_TLS_KEYSTORE_FILE_PATH)).doReturn(true)
        whenever(configMock.getString(BOOT_REST_TLS_KEYSTORE_FILE_PATH)).doReturn(keyStorePath)
        whenever(configMock.getString(BOOT_REST_TLS_KEYSTORE_PASSWORD)).doReturn(password)

        val keyStoreInfo = service.getOrCreateKeyStoreInfo(configMock)
        Assertions.assertEquals(Path.of(keyStorePath), keyStoreInfo.path)
        Assertions.assertEquals(password, keyStoreInfo.password)
    }

    @Test
    fun `can create Keystore from CRT files`() {
        copyResources()

        val configMock = mock<SmartConfig>()
        whenever(configMock.hasPath(BOOT_REST_TLS_CRT_PATH)).doReturn(true)
        whenever(configMock.getString(BOOT_REST_TLS_CRT_PATH)).doReturn(resourcesDir.resolve("server.crt").toString())
        whenever(configMock.getString(BOOT_REST_TLS_KEY_PATH)).doReturn(resourcesDir.resolve("server.key").toString())
        whenever(configMock.getString(BOOT_REST_TLS_CA_CRT_PATH)).doReturn(
            resourcesDir.resolve("ca-chain-bundle.crt").toString()
        )

        val keyStoreInfo = service.getOrCreateKeyStoreInfo(configMock)

        val keyStoreDir = requireNotNull(tempDirectoryPath)
        Assertions.assertEquals(keyStoreDir.resolve(KEYSTORE_NAME), keyStoreInfo.path)
        Assertions.assertEquals(PASSWORD, keyStoreInfo.password)

        // Check Keystore
        val ks = KeyStore.getInstance(KEYSTORE_TYPE)
        keyStoreDir.resolve(KEYSTORE_NAME).inputStream().use {
            ks.load(it, PASSWORD.toCharArray())
        }
        assertTrue(ks.containsAlias(TLS_ENTRY))
        assertThat(ks.getCertificateChain(TLS_ENTRY).asList()).hasSize(3)
    }

    private fun copyResources() {
        listOf("ca-chain-bundle.crt", "server.crt", "server.key").forEach { resourceName ->
            requireNotNull(this::class.java.classLoader.getResourceAsStream(resourceName)).use { inputStream ->
                val bytes = inputStream.readAllBytes()
                resourcesDir.resolve(resourceName).outputStream().use { os -> os.write(bytes) }
            }
        }
    }
}
