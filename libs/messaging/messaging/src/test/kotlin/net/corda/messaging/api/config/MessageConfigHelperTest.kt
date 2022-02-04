package net.corda.messaging.api.config

import com.typesafe.config.ConfigFactory
import net.corda.libs.configuration.SmartConfigFactory
import net.corda.messaging.api.exception.CordaMessageAPIConfigException
import net.corda.schema.configuration.ConfigKeys.BOOT_CONFIG
import net.corda.schema.configuration.ConfigKeys.MESSAGING_CONFIG
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class MessageConfigHelperTest {

    private val smartConfigFactory = SmartConfigFactory.create(ConfigFactory.empty())

    @Test
    fun `messaging config correctly built from boot and messaging sections`() {
        val bootConfig = smartConfigFactory.create(ConfigFactory.parseMap(mapOf("foo" to 1, "bar" to 2)))
        val messagingConfig = smartConfigFactory.create(ConfigFactory.parseMap(mapOf("foo" to 3)))
        val configMap = mapOf(BOOT_CONFIG to bootConfig, MESSAGING_CONFIG to messagingConfig)
        val outputConfig = configMap.toMessagingConfig()
        assertEquals(smartConfigFactory.create(ConfigFactory.parseMap(mapOf("foo" to 3, "bar" to 2))), outputConfig)
    }

    @Test
    fun `error thrown if either section is missing`() {
        val bootConfig = smartConfigFactory.create(ConfigFactory.parseMap(mapOf("foo" to 1, "bar" to 2)))
        val messagingConfig = smartConfigFactory.create(ConfigFactory.parseMap(mapOf("foo" to 3)))
        val map1 = mapOf(BOOT_CONFIG to bootConfig)
        val map2 = mapOf(MESSAGING_CONFIG to messagingConfig)
        assertThrows<CordaMessageAPIConfigException> {
            map1.toMessagingConfig()
        }
        assertThrows<CordaMessageAPIConfigException> {
            map2.toMessagingConfig()
        }
    }
}