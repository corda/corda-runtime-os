package net.corda.cipher.suite.impl.config

import com.typesafe.config.ConfigException
import com.typesafe.config.ConfigFactory
import net.corda.crypto.CryptoCategories
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.Duration
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
            "cipherSuite" to mapOf(
                "schemeMetadataProvider" to "customSchemeMetadataProvider",
                "signatureVerificationProvider" to "customSignatureVerificationProvider",
                "digestProvider" to "customDigestProvider",
            ),
            "default" to mapOf(
                "default" to mapOf(
                    "serviceName" to "default",
                    "timeout" to "1",
                    "defaultSignatureScheme" to "CORDA.EDDSA.ED25519",
                    "serviceConfig" to mapOf(
                        "passphrase" to "pwdD",
                        "salt" to "saltD"
                    )
                )
            ),
            "member123" to mapOf(
                "default" to mapOf(
                    "serviceName" to "default",
                    "timeout" to "3",
                    "defaultSignatureScheme" to "CORDA.ECDSA.SECP256K1",
                    "serviceConfig" to mapOf(
                        "passphrase" to "pwd123",
                        "salt" to "salt123"
                    )
                ),
                "LEDGER" to mapOf(
                    "serviceName" to "UTIMACO",
                    "timeout" to "2",
                    "defaultSignatureScheme" to "CORDA.ECDSA.SECP256R1",
                    "serviceConfig" to mapOf(
                        "password" to "pwd"
                    )
                )
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
        assertEquals("customSchemeMetadataProvider", config.cipherSuite.schemeMetadataProvider)
        assertEquals("customSignatureVerificationProvider", config.cipherSuite.signatureVerificationProvider)
        assertEquals("customDigestProvider", config.cipherSuite.digestProvider)
        val member = config.getMember("member123")
        val default = member.default
        assertEquals("default", default.serviceName)
        assertEquals(Duration.ofSeconds(3), default.timeout)
        assertEquals("CORDA.ECDSA.SECP256K1", default.defaultSignatureScheme)
        assertEquals("pwd123", default.serviceConfig["passphrase"])
        assertEquals("salt123", default.serviceConfig["salt"])
        val ledger = member.getCategory(CryptoCategories.LEDGER)
        assertEquals("UTIMACO", ledger.serviceName)
        assertEquals(Duration.ofSeconds(2), ledger.timeout)
        assertEquals("CORDA.ECDSA.SECP256R1", ledger.defaultSignatureScheme)
        assertEquals("pwd", ledger.serviceConfig["password"])
    }

    @Test
    fun `Should return object with default values if 'cipherSuite' is not specified`() {
        val config = CryptoLibraryConfig(ConfigFactory.empty())
        assertEquals("default", config.cipherSuite.schemeMetadataProvider)
        assertEquals("default", config.cipherSuite.signatureVerificationProvider)
        assertEquals("default", config.cipherSuite.digestProvider)
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
    fun `CipherSuiteConfig should return default values if the value is not provided`() {
        val config = CipherSuiteConfig(ConfigFactory.empty())
        assertEquals("default", config.schemeMetadataProvider)
        assertEquals("default", config.signatureVerificationProvider)
        assertEquals("default", config.digestProvider)    }

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
    fun `Should fail if neither the member id nor 'default' path is not supplied`() {
        val config = CryptoLibraryConfig(ConfigFactory.empty())
        assertThrows<ConfigException.Missing> {
            config.getMember(UUID.randomUUID().toString())
        }
    }

    @Test
    fun `Should return default member config if the member id path is not supplied`() {
        val config = CryptoLibraryConfig(ConfigFactory.parseMap(mapOf(
            "default" to mapOf(
                "default" to mapOf(
                    "serviceName" to "default",
                    "timeout" to "1",
                    "defaultSignatureScheme" to "CORDA.EDDSA.ED25519",
                    "serviceConfig" to mapOf(
                        "passphrase" to "pwdD",
                        "salt" to "saltD"
                    )
                )
            )
        )))
        val member = config.getMember("123")
        val default = member.default
        assertEquals("default", default.serviceName)
        assertEquals(Duration.ofSeconds(1), default.timeout)
        assertEquals("CORDA.EDDSA.ED25519", default.defaultSignatureScheme)
        assertEquals("pwdD", default.serviceConfig["passphrase"])
        assertEquals("saltD", default.serviceConfig["salt"])
        val ledger = member.getCategory(CryptoCategories.LEDGER)
        assertEquals("default", ledger.serviceName)
        assertEquals(Duration.ofSeconds(1), ledger.timeout)
        assertEquals("CORDA.EDDSA.ED25519", ledger.defaultSignatureScheme)
        assertEquals("pwdD", ledger.serviceConfig["passphrase"])
        assertEquals("saltD", ledger.serviceConfig["salt"])
    }
}