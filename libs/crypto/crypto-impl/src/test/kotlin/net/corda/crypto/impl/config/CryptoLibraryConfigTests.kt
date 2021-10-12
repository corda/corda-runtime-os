package net.corda.crypto.impl.config

import net.corda.crypto.CryptoCategories
import net.corda.crypto.impl.dev.InMemoryKeyValuePersistenceFactoryProvider
import net.corda.v5.cipher.suite.config.CryptoServiceConfig
import net.corda.v5.cipher.suite.schemes.ECDSA_SECP256R1_CODE_NAME
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
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
            "keyCache" to mapOf(
                "expireAfterAccessMins" to "90",
                "maximumSize" to "25",
                "persistenceConfig" to mapOf(
                    "url" to "keyPersistenceUrl"
                )
            ),
            "mngCache" to mapOf(
                "factoryName" to InMemoryKeyValuePersistenceFactoryProvider.NAME,
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
        assertEquals(CryptoPersistenceConfig.DEFAULT_FACTORY_NAME, config.keyCache.factoryName)
        assertEquals(90, config.keyCache.expireAfterAccessMins)
        assertEquals(25, config.keyCache.maximumSize)
        assertEquals(CryptoPersistenceConfig.DEFAULT_FACTORY_NAME, config.keyCache.factoryName)
        assertEquals("keyPersistenceUrl", config.keyCache.persistenceConfig.getString("url"))
        assertEquals(InMemoryKeyValuePersistenceFactoryProvider.NAME, config.mngCache.factoryName)
        assertEquals(120, config.mngCache.expireAfterAccessMins)
        assertEquals(50, config.mngCache.maximumSize)
        assertEquals(InMemoryKeyValuePersistenceFactoryProvider.NAME, config.mngCache.factoryName)
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
    fun `CipherSuiteConfig should return default values if the value is not provided`() {
        val config = CipherSuiteConfig(emptyMap())
        assertEquals("default", config.schemeMetadataProvider)
        assertEquals("default", config.signatureVerificationProvider)
        assertEquals("default", config.digestProvider)
    }

    @Test
    @Timeout(5)
    fun `Should use default values if the 'keyCache' path is not supplied`() {
        val config = CryptoLibraryConfigImpl(emptyMap())
        assertEquals(CryptoPersistenceConfig.DEFAULT_FACTORY_NAME, config.keyCache.factoryName)
        assertEquals(60, config.keyCache.expireAfterAccessMins)
        assertEquals(100, config.keyCache.maximumSize)
        assertEquals(CryptoPersistenceConfig.DEFAULT_FACTORY_NAME, config.keyCache.factoryName)
        assertTrue(config.keyCache.persistenceConfig.isEmpty())    }

    @Test
    @Timeout(5)
    fun `Should use default values if the 'mngCache' path is not supplied`() {
        val config = CryptoLibraryConfigImpl(emptyMap())
        assertEquals(CryptoPersistenceConfig.DEFAULT_FACTORY_NAME, config.mngCache.factoryName)
        assertEquals(60, config.mngCache.expireAfterAccessMins)
        assertEquals(100, config.mngCache.maximumSize)
        assertEquals(CryptoPersistenceConfig.DEFAULT_FACTORY_NAME, config.mngCache.factoryName)
        assertTrue(config.mngCache.persistenceConfig.isEmpty())
    }

    @Test
    @Timeout(5)
    fun `Should use default values when neither the member id nor 'default' path is not supplied`() {
        val config = CryptoLibraryConfigImpl(emptyMap())
        val member = config.getMember(UUID.randomUUID().toString()).getCategory(CryptoCategories.LEDGER)
        assertEquals(CryptoServiceConfig.DEFAULT_SERVICE_NAME, member.serviceName)
        assertEquals(Duration.ofSeconds(5), member.timeout)
        assertEquals(ECDSA_SECP256R1_CODE_NAME, member.defaultSignatureScheme)
        assertTrue(member.serviceConfig.isEmpty())
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