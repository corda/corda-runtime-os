package net.corda.messaging.kafka.subscription.net.corda.messagebus.kafka.config

import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory
import net.corda.libs.configuration.SmartConfig
import net.corda.libs.configuration.SmartConfigFactory
import net.corda.messagebus.kafka.config.KafkaConfigMergerImpl
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class KafkaConfigMergerImplTest {
    companion object {
        private const val TEST_BOOT_CONFIG = "test_boot.conf"
    }

    private val smartConfigFactory = SmartConfigFactory.createWithoutSecurityServices()
    private val merger = KafkaConfigMergerImpl()

    private fun assertResultingConfig(result: Config) {
        assertThat(result.getString("bus.busType")).isEqualTo("KAFKA")
        assertThat(result.getString("bus.kafkaProperties.common.bootstrap.servers")).isEqualTo("localhost:9092")
    }

    @Test
    fun `empty messaging config can be merged with boot config`(){
        val bootConfig = loadTestConfig()
        val messagingConfig = smartConfigFactory.create(ConfigFactory.empty())

        val result = merger.getMessagingConfig(bootConfig, messagingConfig)
        assertResultingConfig(result)
    }

    @Test
    fun `existing messaging config can be merged with boot config with boot config taking precendence`(){
        val bootConfig = loadTestConfig()
        val messagingConfig = smartConfigFactory.create(ConfigFactory.parseMap(mapOf(
            "kafka.bus.busType" to "UNKNOWN"
        )))

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
