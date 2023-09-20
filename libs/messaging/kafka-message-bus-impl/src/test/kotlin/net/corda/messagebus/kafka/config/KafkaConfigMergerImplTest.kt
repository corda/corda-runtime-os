package net.corda.messaging.kafka.subscription.net.corda.messagebus.kafka.config

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

    @Test
    fun `empty messaging config can be merged with boot config`(){
        val bootConfig = loadTestConfig(TEST_BOOT_CONFIG)
        val messagingConfig = smartConfigFactory.create(ConfigFactory.empty())

        val result = merger.getMessagingConfig(bootConfig, messagingConfig)

        assertThat(result.getString("bus.busType")).isEqualTo("KAFKA")
        assertThat(result.getString("bus.kafkaProperties.common.bootstrap.servers")).isEqualTo("localhost:9092")
        assertThat(result.getString("stateManager.type")).isEqualTo("DATABASE")
        assertThat(result.getString("stateManager.database.user")).isEqualTo("sampleuser")
        assertThat(result.getString("stateManager.database.pass")).isEqualTo("samplepass")
        assertThat(result.getString("stateManager.database.jdbc.url")).isEqualTo("samplestatemanager")
        assertThat(result.getInt("stateManager.database.pool.idleTimeoutSeconds")).isEqualTo(120)
        assertThat(result.getInt("stateManager.database.pool.keepAliveTimeSeconds")).isEqualTo(0)
        assertThat(result.getInt("stateManager.database.pool.maxLifetimeSeconds")).isEqualTo(1800)
        assertThat(result.getInt("stateManager.database.pool.maxSize")).isEqualTo(5)
        assertThat(result.getInt("stateManager.database.pool.minSize")).isEqualTo(1)
        assertThat(result.getInt("stateManager.database.pool.validationTimeoutSeconds")).isEqualTo(5)
    }

    @Test
    fun `existing messaging config can be merged with boot config with boot config taking precendence`(){
        val bootConfig = loadTestConfig(TEST_BOOT_CONFIG)
        val messagingConfig = smartConfigFactory.create(ConfigFactory.parseMap(mapOf(
            "stateManager.type" to "UNKNOWN",
            "kafka.bus.busType" to "UNKNOWN"
        )))

        val result = merger.getMessagingConfig(bootConfig, messagingConfig)

        assertThat(result.getString("bus.busType")).isEqualTo("KAFKA")
        assertThat(result.getString("bus.kafkaProperties.common.bootstrap.servers")).isEqualTo("localhost:9092")
        assertThat(result.getString("stateManager.type")).isEqualTo("DATABASE")
        assertThat(result.getString("stateManager.database.user")).isEqualTo("sampleuser")
        assertThat(result.getString("stateManager.database.pass")).isEqualTo("samplepass")
        assertThat(result.getString("stateManager.database.jdbc.url")).isEqualTo("samplestatemanager")
        assertThat(result.getInt("stateManager.database.pool.idleTimeoutSeconds")).isEqualTo(120)
        assertThat(result.getInt("stateManager.database.pool.keepAliveTimeSeconds")).isEqualTo(0)
        assertThat(result.getInt("stateManager.database.pool.maxLifetimeSeconds")).isEqualTo(1800)
        assertThat(result.getInt("stateManager.database.pool.maxSize")).isEqualTo(5)
        assertThat(result.getInt("stateManager.database.pool.minSize")).isEqualTo(1)
        assertThat(result.getInt("stateManager.database.pool.validationTimeoutSeconds")).isEqualTo(5)
    }

    private fun loadTestConfig(resource: String): SmartConfig {
        val url = this::class.java.classLoader.getResource(resource)
            ?: throw IllegalArgumentException("Failed to find $resource")
        val configString = url.openStream().bufferedReader().use {
            it.readText()
        }
        return smartConfigFactory.create(ConfigFactory.parseString(configString))
    }
}
