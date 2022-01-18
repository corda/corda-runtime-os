package net.corda.crypto.component.persistence.config

import net.corda.crypto.impl.config.CryptoConfigMap
import net.corda.v5.cipher.suite.config.CryptoLibraryConfig
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import kotlin.test.assertEquals

class ConfigExtensionsTests {
    @Test
    @Timeout(5)
    fun `Should be able to use all helper properties`() {
        val raw = mapOf<String, Any?>(
            "softPersistence" to mapOf(
                "expireAfterAccessMins" to "91",
                "maximumSize" to "26"
            ),
            "signingPersistence" to mapOf(
                "expireAfterAccessMins" to "92",
                "maximumSize" to "27"
            )
        )
        val config = CryptoLibraryConfigTestImpl(raw)
        assertEquals(91, config.softPersistence.expireAfterAccessMins)
        assertEquals(26, config.softPersistence.maximumSize)
        assertEquals(92, config.signingPersistence.expireAfterAccessMins)
        assertEquals(27, config.signingPersistence.maximumSize)
    }

    @Test
    @Timeout(5)
    fun `Should use default values if the 'defaultCryptoService' path is not supplied`() {
        val config = CryptoLibraryConfigTestImpl(emptyMap())
        assertEquals(60, config.softPersistence.expireAfterAccessMins)
        assertEquals(100, config.softPersistence.maximumSize)
        assertEquals(60, config.signingPersistence.expireAfterAccessMins)
        assertEquals(100, config.signingPersistence.maximumSize)
    }

    @Test
    @Timeout(5)
    fun `CryptoPersistenceConfig should return default values if the value is not provided`() {
        val config = CryptoPersistenceConfig(emptyMap())
        assertEquals(60, config.expireAfterAccessMins)
        assertEquals(100, config.maximumSize)
    }

    private class CryptoLibraryConfigTestImpl(
        map: Map<String, Any?>
    ) : CryptoConfigMap(map), CryptoLibraryConfig
}