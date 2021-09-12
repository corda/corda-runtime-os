package net.corda.components.crypto.config

import com.typesafe.config.ConfigException
import com.typesafe.config.ConfigFactory
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CryptoLibraryConfigTests {
    @Test
    fun `Should be able to use all helper properties`() {
        val raw = ConfigFactory.parseMap(mapOf<String, Any>(
            "rpc" to mapOf(
                "groupName" to "rpcGroupName",
                "clientName" to "rpcClientName",
                "signingRequestTopic" to "rpcSigningRequestTopic",
                "freshKeysRequestTopic" to "rpcFreshKeysRequestTopic"

            ),
            "keyCache" to mapOf(
                "expireAfterAccessMins" to "90",
                "maximumSize" to "25",
                "persistenceConfig" to mapOf(
                    "url" to "keyPersistenceUrl"
                )
            ),
            "mngCache" to mapOf(
                "expireAfterAccessMins" to "120",
                "maximumSize" to "50",
                "persistenceConfig" to mapOf(
                    "url" to "mngPersistenceUrl"
                )
            ),
            "default" to mapOf(
                "p1" to "v1"
            ),
            "member123" to mapOf(
                "p2" to "v2"
            )
        ))
        val config = CryptoLibraryConfig(raw)
        assertEquals("rpcGroupName", config.rpc.groupName)
        assertEquals("rpcClientName", config.rpc.clientName)
        assertEquals("rpcSigningRequestTopic", config.rpc.signingRequestTopic)
        assertEquals("rpcFreshKeysRequestTopic", config.rpc.freshKeysRequestTopic)
        assertEquals(90, config.keyCache.expireAfterAccessMins)
        assertEquals(25, config.keyCache.maximumSize)
        assertEquals("keyPersistenceUrl", config.keyCache.persistenceConfig.getString("url"))
        assertEquals(120, config.mngCache.expireAfterAccessMins)
        assertEquals(50, config.mngCache.maximumSize)
        assertEquals("mngPersistenceUrl", config.mngCache.persistenceConfig.getString("url"))
    }

    @Test
    fun `CryptoRpcConfig should return default values if the value is not provided`() {
        val config = CryptoRpcConfig(ConfigFactory.empty())
        assertEquals("crypto.rpc", config.groupName)
        assertEquals("crypto.rpc", config.clientName)
        assertEquals("crypto.rpc.signing", config.signingRequestTopic)
        assertEquals("crypto.rpc.freshKeys", config.freshKeysRequestTopic)
    }

    @Test
    fun `CryptoCacheConfig should return default values if the value is not provided`() {
        val config = CryptoCacheConfig(ConfigFactory.empty())
        assertEquals(60, config.expireAfterAccessMins)
        assertEquals(100, config.maximumSize)
        assertTrue(config.persistenceConfig.isEmpty)
    }

    @Test
    fun `Should fail if the 'rpc' path is not supplied`() {
        val config = CryptoLibraryConfig(ConfigFactory.empty())
        assertThrows<ConfigException.Missing> {
            config.rpc
        }
    }

    @Test
    fun `Should fail if the 'keyCache' path is not supplied`() {
        val config = CryptoLibraryConfig(ConfigFactory.empty())
        assertThrows<ConfigException.Missing> {
            config.keyCache
        }
    }

    @Test
    fun `Should fail if the 'mngCache' path is not supplied`() {
        val config = CryptoLibraryConfig(ConfigFactory.empty())
        assertThrows<ConfigException.Missing> {
            config.mngCache
        }
    }

    @Test
    fun `Should fail if the member id path is not supplied`() {
        val config = CryptoLibraryConfig(ConfigFactory.empty())
        assertThrows<ConfigException.Missing> {
            config.getMember(UUID.randomUUID().toString())
        }
    }
}