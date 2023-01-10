package net.corda.libs.configuration.helper

import com.typesafe.config.ConfigFactory
import net.corda.libs.configuration.SmartConfigFactoryFactory
import net.corda.libs.configuration.exception.CordaAPIConfigException
import net.corda.schema.configuration.ConfigKeys
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class MessageConfigHelperTest {

    private val smartConfigFactory = SmartConfigFactoryFactory(emptyList()).createWithoutSecurityServices()

    @Test
    fun `messaging config correctly built from boot and messaging sections`() {
        val bootConfig = smartConfigFactory.create(ConfigFactory.parseMap(mapOf("foo" to 1, "bar" to 2)))
        val messagingConfig = smartConfigFactory.create(ConfigFactory.parseMap(mapOf("foo" to 3)))
        val configMap = mapOf(ConfigKeys.BOOT_CONFIG to bootConfig, ConfigKeys.MESSAGING_CONFIG to messagingConfig)
        val outputConfig = configMap.getConfig(ConfigKeys.MESSAGING_CONFIG)
        Assertions.assertEquals(smartConfigFactory.create(ConfigFactory.parseMap(mapOf("foo" to 3))), outputConfig)
    }

    @Test
    fun `error thrown if either section is missing`() {
        val bootConfig = smartConfigFactory.create(ConfigFactory.parseMap(mapOf("foo" to 1, "bar" to 2)))
        val map1 = mapOf(ConfigKeys.BOOT_CONFIG to bootConfig)
        assertThrows<CordaAPIConfigException> {
            map1.getConfig(ConfigKeys.MESSAGING_CONFIG)
        }
    }
}