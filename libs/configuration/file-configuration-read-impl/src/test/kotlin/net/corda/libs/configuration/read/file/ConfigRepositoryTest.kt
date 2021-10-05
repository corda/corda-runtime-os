package net.corda.libs.configuration.read.file

import org.assertj.core.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class ConfigRepositoryTest {
    private lateinit var configRepository: ConfigRepository

    @BeforeEach
    fun beforeEach() {
        configRepository = ConfigRepository()
    }

    @Test
    fun testStoreAndGetConfiguration() {
        val configMap = ConfigUtil.testConfigMap()
        configRepository.storeConfiguration(configMap)
        val returnedConfig = configRepository.getConfigurations()

        Assertions.assertThat(returnedConfig.keys).isEqualTo(configMap.keys)
    }

    @Test
    fun testUpdateConfiguration() {
        val configMap = ConfigUtil.testConfigMap()
        configRepository.storeConfiguration(configMap)

        configRepository.updateConfiguration("corda.database", ConfigUtil.databaseConfig(5.7))
        configRepository.updateConfiguration("corda.security", ConfigUtil.securityConfig(5.5))

        val returnedConfig = configRepository.getConfigurations()

        Assertions.assertThat(returnedConfig.keys).isEqualTo(configMap.keys)
        Assertions.assertThat(returnedConfig["corda.database"]?.getDouble("componentVersion")).isEqualTo(5.7)
        Assertions.assertThat(returnedConfig["corda.security"]?.getDouble("componentVersion")).isEqualTo(5.5)
    }


}