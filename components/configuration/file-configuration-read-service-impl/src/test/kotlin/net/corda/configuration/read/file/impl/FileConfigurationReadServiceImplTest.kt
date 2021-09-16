package net.corda.configuration.read.file.impl

import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory
import com.typesafe.config.ConfigValueFactory
import net.corda.configuration.read.ConfigurationHandler
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argThat
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import java.io.File
import java.nio.file.Files
import java.nio.file.Path

class FileConfigurationReadServiceImplTest {

    private companion object {
        const val TEMP_DIRECTORY_PREFIX = "file-config-read-service-test"
        const val TEST_CONF_NAME = "test.conf"
    }

    private val service = FileConfigurationReadServiceImpl()
    private lateinit var tempDirectoryPath: Path
    private lateinit var tempConfigFilePath: Path

    @BeforeEach
    fun beforeEach() {
        createTempTestConfig()
    }

    @AfterEach
    fun afterEach() {
        File(tempDirectoryPath.toUri()).deleteRecursively()
    }

    @Test
    fun `starting the service will update listeners with the config`() {
        val configHandler1: ConfigurationHandler = mock()
        val configHandler2: ConfigurationHandler = mock()

        service.bootstrapConfig(bootstrapConfig())

        service.use {
            it.registerForUpdates(configHandler1)
            verify(configHandler1, never()).onNewConfiguration(any(), any())

            it.start()
            verify(configHandler1, times(1))
                .onNewConfiguration(eq(setOf(tempConfigFilePath.toString())),
                    argThat { get(tempConfigFilePath.toString())!!.hasPath("corda.rpc.address") })

            it.registerForUpdates(configHandler2)
            verify(configHandler2, times(1))
                .onNewConfiguration(eq(setOf(tempConfigFilePath.toString())),
                    argThat { get(tempConfigFilePath.toString())!!.hasPath("corda.rpc.address") })
        }
    }

    private fun createTempTestConfig() {
        tempDirectoryPath = Files.createTempDirectory(TEMP_DIRECTORY_PREFIX)
        val testConfContent = File(this::class.java.classLoader.getResource(TEST_CONF_NAME)!!.toURI()).inputStream().readAllBytes()
        tempConfigFilePath = Path.of(tempDirectoryPath.toString(), TEST_CONF_NAME)
        tempConfigFilePath.toFile().writeBytes(testConfContent)
    }

    private fun bootstrapConfig(): Config {
        return ConfigFactory
            .empty()
            .withValue(FileConfigurationReadServiceImpl.CONFIG_FILE_NAME, ConfigValueFactory.fromAnyRef(tempConfigFilePath.toString()))
    }
}