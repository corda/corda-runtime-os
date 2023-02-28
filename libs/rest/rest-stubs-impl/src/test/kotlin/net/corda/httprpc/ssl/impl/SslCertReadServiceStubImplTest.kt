package net.corda.libs.config.net.corda.rest.ssl.impl

import net.corda.rest.ssl.impl.SslCertReadServiceStubImpl
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import java.io.File
import java.nio.file.Files
import java.nio.file.Path

class SslCertReadServiceStubImplTest {

    private lateinit var tempDirectoryPath: Path

    private val service = SslCertReadServiceStubImpl(
        createDirectory = {
            Files.createTempDirectory("rest-ssl").also {
                this.tempDirectoryPath = it
            }
        }
    )

    @AfterEach
    fun afterEach() {
        File(tempDirectoryPath.toUri()).deleteRecursively()
    }

    @Test
    fun `creates a keystore in tmp directory`() {
        service.getOrCreateKeyStore()
        Assertions.assertTrue(
            File(
                Path.of(tempDirectoryPath.toString(), SslCertReadServiceStubImpl.KEYSTORE_NAME).toUri()
            ).exists()
        )
    }

    @Test
    fun `create returns path and password of keystore`() {
        val keyStoreInfo = service.getOrCreateKeyStore()
        Assertions.assertEquals(
            Path.of(tempDirectoryPath.toString(), SslCertReadServiceStubImpl.KEYSTORE_NAME),
            keyStoreInfo.path
        )
        Assertions.assertEquals(SslCertReadServiceStubImpl.PASSWORD, keyStoreInfo.password)
    }

    @Test
    fun `deletes keystore when stopped`() {
        service.getOrCreateKeyStore()
        Assertions.assertTrue(
            File(
                Path.of(tempDirectoryPath.toString(), SslCertReadServiceStubImpl.KEYSTORE_NAME).toUri()
            ).exists()
        )
        service.stop()
        Assertions.assertFalse(
            File(
                Path.of(tempDirectoryPath.toString(), SslCertReadServiceStubImpl.KEYSTORE_NAME).toUri()
            ).exists()
        )
    }
}