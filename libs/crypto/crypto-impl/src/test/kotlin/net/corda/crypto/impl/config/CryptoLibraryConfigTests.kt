package net.corda.crypto.impl.config

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CryptoLibraryConfigTests {
    @Test
    @Timeout(5)
    fun `Should be able to use all helper properties`() {
        val raw = mapOf<String, Any?>(
            "defaultCryptoService" to mapOf(
                "expireAfterAccessMins" to "90",
                "maximumSize" to "25",
                "persistenceConfig" to mapOf(
                    "url" to "keyPersistenceUrl"
                )
            ),
            "publicKeys" to mapOf(
                "factoryName" to "dev",
                "expireAfterAccessMins" to "120",
                "maximumSize" to "50",
                "persistenceConfig" to mapOf(
                    "url" to "mngPersistenceUrl"
                )
            )
        )
        val config = CryptoLibraryConfigImpl(raw)
        assertFalse(config.isDev)
        assertEquals(CryptoPersistenceConfig.DEFAULT_FACTORY_NAME, config.defaultCryptoService.factoryName)
        assertEquals(90, config.defaultCryptoService.expireAfterAccessMins)
        assertEquals(25, config.defaultCryptoService.maximumSize)
        assertEquals(CryptoPersistenceConfig.DEFAULT_FACTORY_NAME, config.defaultCryptoService.factoryName)
        assertEquals("keyPersistenceUrl", config.defaultCryptoService.persistenceConfig.getString("url"))
        assertEquals("dev", config.publicKeys.factoryName)
        assertEquals(120, config.publicKeys.expireAfterAccessMins)
        assertEquals(50, config.publicKeys.maximumSize)
        assertEquals("dev", config.publicKeys.factoryName)
        assertEquals("mngPersistenceUrl", config.publicKeys.persistenceConfig.getString("url"))
    }

    @Test
    @Timeout(5)
    fun `CryptoCacheConfig should return default values if the value is not provided`() {
        val config = CryptoPersistenceConfig(emptyMap())
        assertEquals(CryptoPersistenceConfig.DEFAULT_FACTORY_NAME, config.factoryName)
        assertEquals(60, config.expireAfterAccessMins)
        assertEquals(100, config.maximumSize)
        assertEquals(CryptoPersistenceConfig.DEFAULT_FACTORY_NAME, config.factoryName)
        assertTrue(config.persistenceConfig.isEmpty())
    }

    @Test
    @Timeout(5)
    fun `CryptoCacheConfig default object should return default values`() {
        val config = CryptoPersistenceConfig.default
        assertEquals(CryptoPersistenceConfig.DEFAULT_FACTORY_NAME, config.factoryName)
        assertEquals(60, config.expireAfterAccessMins)
        assertEquals(100, config.maximumSize)
        assertEquals(CryptoPersistenceConfig.DEFAULT_FACTORY_NAME, config.factoryName)
        assertTrue(config.persistenceConfig.isEmpty())
    }

    @Test
    @Timeout(5)
    fun `Should use default values if the 'defaultCryptoService' path is not supplied`() {
        val config = CryptoLibraryConfigImpl(emptyMap())
        assertEquals(CryptoPersistenceConfig.DEFAULT_FACTORY_NAME, config.defaultCryptoService.factoryName)
        assertEquals(60, config.defaultCryptoService.expireAfterAccessMins)
        assertEquals(100, config.defaultCryptoService.maximumSize)
        assertEquals(CryptoPersistenceConfig.DEFAULT_FACTORY_NAME, config.defaultCryptoService.factoryName)
        assertTrue(config.defaultCryptoService.persistenceConfig.isEmpty())    }

    @Test
    @Timeout(5)
    fun `Should use default values if the 'publicKeys' path is not supplied`() {
        val config = CryptoLibraryConfigImpl(emptyMap())
        assertEquals(CryptoPersistenceConfig.DEFAULT_FACTORY_NAME, config.publicKeys.factoryName)
        assertEquals(60, config.publicKeys.expireAfterAccessMins)
        assertEquals(100, config.publicKeys.maximumSize)
        assertEquals(CryptoPersistenceConfig.DEFAULT_FACTORY_NAME, config.publicKeys.factoryName)
        assertTrue(config.publicKeys.persistenceConfig.isEmpty())
    }

    @Test
    @Timeout(5)
    fun `Should return false if 'isDev' path is not supplied`() {
        val config = CryptoLibraryConfigImpl(emptyMap())
        assertFalse(config.isDev)
    }

    @Test
    @Timeout(5)
    fun `Should return 'isDev' value`() {
        val config = CryptoLibraryConfigImpl(
            mapOf(
                "isDev" to "true"
            )
        )
        assertTrue(config.isDev)
    }
}