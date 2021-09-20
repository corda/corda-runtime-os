package net.corda.crypto.impl.config

import net.corda.crypto.CryptoCategories
import net.corda.data.crypto.wire.freshkeys.WireFreshKeysRequest
import net.corda.data.crypto.wire.freshkeys.WireFreshKeysResponse
import net.corda.data.crypto.wire.signing.WireSigningRequest
import net.corda.data.crypto.wire.signing.WireSigningResponse
import net.corda.v5.crypto.exceptions.CryptoConfigurationException
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import org.junit.jupiter.api.assertThrows
import java.time.Duration
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CryptoLibraryConfigTests {
    @Test
    @Timeout(5)
    fun `Should be able to use all helper properties`() {
        val raw = mapOf<String, Any?>(
            "rpc" to mapOf(
                "groupName" to "rpcGroupName",
                "clientName" to "rpcClientName",
                "signingRequestTopic" to "rpcSigningRequestTopic",
                "freshKeysRequestTopic" to "rpcFreshKeysRequestTopic",
                "clientTimeout" to "11",
                "clientRetries" to "77"
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
        )
        val config = CryptoLibraryConfigImpl(raw)
        assertFalse(config.isDev)
        assertEquals("rpcGroupName", config.rpc.groupName)
        assertEquals("rpcClientName", config.rpc.clientName)
        assertEquals("rpcSigningRequestTopic", config.rpc.signingRequestTopic)
        assertEquals("rpcFreshKeysRequestTopic", config.rpc.freshKeysRequestTopic)
        assertEquals(11, config.rpc.clientTimeout)
        assertEquals(77, config.rpc.clientRetries)
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
    @Timeout(5)
    fun `Should return object with default values if 'cipherSuite' is not specified`() {
        val config = CryptoLibraryConfigImpl(emptyMap())
        assertEquals("default", config.cipherSuite.schemeMetadataProvider)
        assertEquals("default", config.cipherSuite.signatureVerificationProvider)
        assertEquals("default", config.cipherSuite.digestProvider)
    }

    @Test
    @Timeout(5)
    fun `CryptoRpcConfig should return default values if the value is not provided`() {
        val config = CryptoRpcConfig(emptyMap())
        assertEquals("crypto.rpc", config.groupName)
        assertEquals("crypto.rpc", config.clientName)
        assertEquals("crypto.rpc.signing", config.signingRequestTopic)
        assertEquals("crypto.rpc.freshKeys", config.freshKeysRequestTopic)
        assertEquals(15, config.clientTimeout)
        assertEquals(1, config.clientRetries)
    }

    @Test
    @Timeout(5)
    fun `CryptoRpcConfig should create RPC config for signing service`() {
        val raw = mapOf(
            "groupName" to "rpcGroupName",
            "clientName" to "rpcClientName",
            "signingRequestTopic" to "rpcSigningRequestTopic",
            "freshKeysRequestTopic" to "rpcFreshKeysRequestTopic"

        )
        val config = CryptoRpcConfig(raw)
        val rpc = config.signingRpcConfig
        assertEquals("rpcGroupName", rpc.groupName)
        assertEquals("rpcClientName", rpc.clientName)
        assertEquals("rpcSigningRequestTopic", rpc.requestTopic)
        assertEquals(WireSigningRequest::class.java, rpc.requestType)
        assertEquals(WireSigningResponse::class.java, rpc.responseType)
    }

    @Test
    @Timeout(5)
    fun `CryptoRpcConfig should create RPC config for fresh keys service`() {
        val raw = mapOf(
            "groupName" to "rpcGroupName",
            "clientName" to "rpcClientName",
            "signingRequestTopic" to "rpcSigningRequestTopic",
            "freshKeysRequestTopic" to "rpcFreshKeysRequestTopic"

        )
        val config = CryptoRpcConfig(raw)
        val rpc = config.freshKeysRpcConfig
        assertEquals("rpcGroupName", rpc.groupName)
        assertEquals("rpcClientName", rpc.clientName)
        assertEquals("rpcFreshKeysRequestTopic", rpc.requestTopic)
        assertEquals(WireFreshKeysRequest::class.java, rpc.requestType)
        assertEquals(WireFreshKeysResponse::class.java, rpc.responseType)
    }

    @Test
    @Timeout(5)
    fun `CryptoCacheConfig should return default values if the value is not provided`() {
        val config = CryptoCacheConfig(emptyMap())
        assertEquals(60, config.expireAfterAccessMins)
        assertEquals(100, config.maximumSize)
        assertTrue(config.persistenceConfig.isEmpty())
    }

    @Test
    @Timeout(5)
    fun `CryptoCacheConfig default object should return default values`() {
        val config = CryptoCacheConfig.default
        assertEquals(60, config.expireAfterAccessMins)
        assertEquals(100, config.maximumSize)
        assertTrue(config.persistenceConfig.isEmpty())
    }

    @Test
    @Timeout(5)
    fun `CipherSuiteConfig should return default values if the value is not provided`() {
        val config = CipherSuiteConfig(emptyMap())
        assertEquals("default", config.schemeMetadataProvider)
        assertEquals("default", config.signatureVerificationProvider)
        assertEquals("default", config.digestProvider)
    }

    @Test
    @Timeout(5)
    fun `Should fail if the 'rpc' path is not supplied`() {
        val config = CryptoLibraryConfigImpl(emptyMap())
        assertThrows<CryptoConfigurationException> {
            config.rpc
        }
    }

    @Test
    @Timeout(5)
    fun `Should fail if the 'keyCache' path is not supplied`() {
        val config = CryptoLibraryConfigImpl(emptyMap())
        assertThrows<CryptoConfigurationException> {
            config.keyCache
        }
    }

    @Test
    @Timeout(5)
    fun `Should fail if the 'mngCache' path is not supplied`() {
        val config = CryptoLibraryConfigImpl(emptyMap())
        assertThrows<CryptoConfigurationException> {
            config.mngCache
        }
    }

    @Test
    @Timeout(5)
    fun `Should fail if neither the member id nor 'default' path is not supplied`() {
        val config = CryptoLibraryConfigImpl(emptyMap())
        assertThrows<CryptoConfigurationException> {
            config.getMember(UUID.randomUUID().toString())
        }
    }

    @Test
    @Timeout(5)
    fun `Should return default member config if the member id path is not supplied`() {
        val config = CryptoLibraryConfigImpl(
            mapOf(
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
            )
        )
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