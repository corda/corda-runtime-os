package net.corda.messagebus.db.configuration

import com.typesafe.config.ConfigFactory
import net.corda.libs.configuration.SmartConfig
import net.corda.libs.configuration.SmartConfigFactory
import net.corda.messagebus.api.configuration.AdminConfig
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class MessageBusConfigResolverTest {
    companion object {
        private const val TEST_CONFIG = "test.conf"
    }

    private val smartConfigFactory = SmartConfigFactory.createWithoutSecurityServices()

    @Test
    fun `DB admin config can be resolved`() {
        val target = MessageBusConfigResolver(smartConfigFactory)
        val testConfig = loadTestConfig(TEST_CONFIG)
        val results = target.resolve(testConfig, AdminConfig("client1"))

        assertThat(results.jdbcUrl).isEqualTo("connectionUrl")
        assertThat(results.jdbcUser).isEqualTo("user1")
        assertThat(results.jdbcPass).isEqualTo("pass1")
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
