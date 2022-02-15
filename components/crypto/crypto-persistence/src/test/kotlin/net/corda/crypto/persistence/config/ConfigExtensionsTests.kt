package net.corda.crypto.persistence.config

import com.typesafe.config.ConfigFactory
import net.corda.libs.configuration.SmartConfigFactory
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import kotlin.test.assertEquals

class ConfigExtensionsTests {
    @Test
    @Timeout(300)
    fun `Should be able to use all helper properties`() {
        val config = SmartConfigFactory.create(ConfigFactory.empty())
            .create(
                ConfigFactory.parseMap(
                    mapOf(
                        "softPersistence" to mapOf(
                            "expireAfterAccessMins" to "91",
                            "maximumSize" to "26"
                        ),
                        "signingPersistence" to mapOf(
                            "expireAfterAccessMins" to "92",
                            "maximumSize" to "27"
                        )
                    )
                )
            )
        assertEquals(91, config.softPersistence.expireAfterAccessMins)
        assertEquals(26, config.softPersistence.maximumSize)
        assertEquals(92, config.signingPersistence.expireAfterAccessMins)
        assertEquals(27, config.signingPersistence.maximumSize)
    }

    @Test
    @Timeout(300)
    fun `Should use default values if the 'softPersistence' and 'signingPersistence' paths are not supplied`() {
        val config = SmartConfigFactory.create(ConfigFactory.empty()).create(ConfigFactory.empty())
        assertEquals(60, config.softPersistence.expireAfterAccessMins)
        assertEquals(100, config.softPersistence.maximumSize)
        assertEquals(60, config.signingPersistence.expireAfterAccessMins)
        assertEquals(100, config.signingPersistence.maximumSize)
    }

    @Test
    @Timeout(300)
    fun `CryptoPersistenceConfig should return default values if the value is not provided`() {
        val config = CryptoPersistenceConfig(
            SmartConfigFactory.create(ConfigFactory.empty()).create(ConfigFactory.empty())
        )
        assertEquals(60, config.expireAfterAccessMins)
        assertEquals(100, config.maximumSize)
    }
}