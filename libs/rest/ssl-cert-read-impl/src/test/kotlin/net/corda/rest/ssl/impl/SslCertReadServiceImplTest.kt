package net.corda.rest.ssl.impl

import net.corda.libs.configuration.SmartConfig
import net.corda.schema.configuration.BootConfig
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.io.File
import java.nio.file.Files
import java.nio.file.Path

class SslCertReadServiceImplTest {

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
        whenever(configMock.hasPath(BootConfig.BOOT_REST_TLS_KEYSTORE_FILE_PATH)).doReturn(true)
        whenever(configMock.getString(BootConfig.BOOT_REST_TLS_KEYSTORE_FILE_PATH)).doReturn(keyStorePath)
        whenever(configMock.getString(BootConfig.BOOT_REST_TLS_KEYSTORE_PASSWORD)).doReturn(password)

        val keyStoreInfo = service.getOrCreateKeyStoreInfo(configMock)
        Assertions.assertEquals(Path.of(keyStorePath), keyStoreInfo.path)
        Assertions.assertEquals(password, keyStoreInfo.password)
    }
}