package net.corda.httprpc.ssl

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File
import java.nio.file.Files
import java.nio.file.Path

class SslCertReadServiceStubImplTest {

    private lateinit var tempDirectoryPath: Path

    private val service = SslCertReadServiceStubImpl(
        createDirectory = {
            Files.createTempDirectory("http-rpc-ssl").also {
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
        service.createKeyStore()
        assertTrue(File(Path.of(tempDirectoryPath.toString(), "ssl-keystore.jks").toUri()).exists())
    }

    @Test
    fun `create returns path and password of keystore`() {
        val keyStoreInfo = service.createKeyStore()
        assertEquals(Path.of(tempDirectoryPath.toString(), "ssl-keystore.jks"), keyStoreInfo.path)
        assertEquals("Corda", keyStoreInfo.password)
    }

    @Test
    fun `deletes keystore when stopped`() {
        service.createKeyStore()
        assertTrue(File(Path.of(tempDirectoryPath.toString(), "ssl-keystore.jks").toUri()).exists())
        service.stop()
        assertFalse(File(Path.of(tempDirectoryPath.toString(), "ssl-keystore.jks").toUri()).exists())
    }
}