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
            "softCryptoService" to mapOf(
                "expireAfterAccessMins" to "90",
                "maximumSize" to "25"
            ),
            "publicKeys" to mapOf(
                "expireAfterAccessMins" to "120",
                "maximumSize" to "50"
            )
        )
        val config = CryptoLibraryConfigImpl(raw)
        assertFalse(config.isDev)
        assertEquals(90, config.softCryptoService.expireAfterAccessMins)
        assertEquals(25, config.softCryptoService.maximumSize)
        assertEquals(120, config.publicKeys.expireAfterAccessMins)
        assertEquals(50, config.publicKeys.maximumSize)
    }

    @Test
    @Timeout(5)
    fun `CryptoCacheConfig should return default values if the value is not provided`() {
        val config = CryptoPersistenceConfig(emptyMap())
        assertEquals(60, config.expireAfterAccessMins)
        assertEquals(100, config.maximumSize)
    }

    @Test
    @Timeout(5)
    fun `CryptoCacheConfig default object should return default values`() {
        val config = CryptoPersistenceConfig.default
        assertEquals(60, config.expireAfterAccessMins)
        assertEquals(100, config.maximumSize)
    }

    @Test
    @Timeout(5)
    fun `Should use default values if the 'defaultCryptoService' path is not supplied`() {
        val config = CryptoLibraryConfigImpl(emptyMap())
        assertEquals(60, config.softCryptoService.expireAfterAccessMins)
        assertEquals(100, config.softCryptoService.maximumSize)
    }

    @Test
    @Timeout(5)
    fun `Should use default values if the 'publicKeys' path is not supplied`() {
        val config = CryptoLibraryConfigImpl(emptyMap())
        assertEquals(60, config.publicKeys.expireAfterAccessMins)
        assertEquals(100, config.publicKeys.maximumSize)
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