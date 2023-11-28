package net.corda.messagebus.db.configuration

import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory
import net.corda.libs.configuration.SmartConfig
import net.corda.libs.configuration.SmartConfigFactory
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class DbBusConfigMergerImplTest {
    companion object {
        private const val TEST_BOOT_CONFIG = "test_boot.conf"
    }

    private val merger = DbBusConfigMergerImpl()
    private val smartConfigFactory = SmartConfigFactory.createWithoutSecurityServices()

    private fun assertResultingConfig(result: Config) {
        assertThat(result.getString("bus.busType")).isEqualTo("DATABASE")
        assertThat(result.getString("bus.dbProperties.user")).isEqualTo("user")
        assertThat(result.getString("bus.dbProperties.pass")).isEqualTo("password")
        assertThat(result.getString("bus.dbProperties.jdbcUrl")).isEqualTo("sampleurlmessagebus")
    }

    @Test
    fun `empty messaging config can be merged with boot config`() {
        val bootConfig = loadTestConfig()
        val messagingConfig = smartConfigFactory.create(ConfigFactory.empty())

        val result = merger.getMessagingConfig(bootConfig, messagingConfig)
        assertResultingConfig(result)
    }

    @Test
    fun `existing messaging config can be merged with boot config`() {
        val bootConfig = loadTestConfig()
        val messagingConfig = smartConfigFactory.create(
            ConfigFactory.parseMap(
                mapOf(
                    "db.bus.busType" to "UNKNOWN"
                )
            )
        )

        val result = merger.getMessagingConfig(bootConfig, messagingConfig)
        assertResultingConfig(result)
    }

    private fun loadTestConfig(): SmartConfig {
        val url = this::class.java.classLoader.getResource(TEST_BOOT_CONFIG)
            ?: throw IllegalArgumentException("Failed to find $TEST_BOOT_CONFIG")
        val configString = url.openStream().bufferedReader().use {
            it.readText()
        }
        return smartConfigFactory.create(ConfigFactory.parseString(configString))
    }
}
