package net.corda.libs.configuration.read.file

import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory
import com.typesafe.config.ConfigValueFactory
import net.corda.libs.configuration.SmartConfigFactory
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.io.File
import java.nio.file.Files
import java.nio.file.Path

class FileConfigReaderImplTest {

    private companion object {
        const val TEMP_DIRECTORY_PREFIX = "file-config-read-service-test"
        const val TEST_CONF_NAME = "test.conf"
    }

    private val configRepository = ConfigRepository()
    private lateinit var service: FileConfigReaderImpl
    private lateinit var tempDirectoryPath: Path
    private lateinit var tempConfigFilePath: Path
    private val configFactory = SmartConfigFactory.create(ConfigFactory.empty())

    @BeforeEach
    fun beforeEach() {
        createTempTestConfig()
        val bootConfig = configFactory.create(bootstrapConfig())
        service = FileConfigReaderImpl(configRepository, bootConfig, bootConfig.factory)
        service.start()
    }

    @AfterEach
    fun afterEach() {
        File(tempDirectoryPath.toUri()).deleteRecursively()
    }

    @Test
    fun `test registerCallback with lambda and bootstrap`() {
        var lambdaFlag = false
        var changedKeys = setOf<String>()
        var configSnapshot = mapOf<String, Config>()

        service.registerCallback { keys: Set<String>, config: Map<String, Config> ->
            lambdaFlag = true
            changedKeys = keys
            configSnapshot = config
        }
        assertThat(lambdaFlag).isTrue
        assertThat(changedKeys.size).isEqualTo(3)
        assertNotNull(configRepository.getConfigurations()["corda.rpc"])
        assertNotNull(configRepository.getConfigurations()["corda.another_rpc"])
        assertNotNull(configRepository.getConfigurations()["corda.boot"])
        assertThat(configSnapshot["corda.rpc"]).isEqualTo(configRepository.getConfigurations()["corda.rpc"])
        assertTrue(configRepository.getConfigurations()["corda.rpc"]!!.hasPath("address"))
        assertTrue(configRepository.getConfigurations()["corda.boot"]!!.hasPath("config.file"))
    }

    @Test
    fun `test that listeners still work after stop start`() {
        var lambdaFlag = false
        var changedKeys = setOf<String>()
        var configSnapshot = mapOf<String, Config>()

        service.registerCallback { keys: Set<String>, config: Map<String, Config> ->
            lambdaFlag = true
            changedKeys = keys
            configSnapshot = config
        }

        service.stop()
        service.start()

        assertThat(lambdaFlag).isTrue
        assertThat(changedKeys.size).isEqualTo(3)
        assertNotNull(configRepository.getConfigurations()["corda.rpc"])
        assertNotNull(configRepository.getConfigurations()["corda.another_rpc"])
        assertNotNull(configRepository.getConfigurations()["corda.boot"])
        assertThat(configSnapshot["corda.rpc"]).isEqualTo(configRepository.getConfigurations()["corda.rpc"])
        assertTrue(configRepository.getConfigurations()["corda.rpc"]!!.hasPath("address"))
        assertTrue(configRepository.getConfigurations()["corda.boot"]!!.hasPath("config.file"))
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
            .withValue(FileConfigReaderImpl.CONFIG_FILE_NAME, ConfigValueFactory.fromAnyRef(tempConfigFilePath.toString()))
    }
}
