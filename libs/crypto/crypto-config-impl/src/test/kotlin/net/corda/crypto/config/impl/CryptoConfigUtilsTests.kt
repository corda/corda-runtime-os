package net.corda.crypto.config.impl

import com.typesafe.config.ConfigFactory
import net.corda.crypto.core.CryptoConsts.SOFT_HSM_SERVICE_NAME
import net.corda.crypto.core.CryptoConsts.SOFT_HSM_ID
import net.corda.crypto.core.aes.KeyCredentials
import net.corda.libs.configuration.SmartConfig
import net.corda.libs.configuration.SmartConfigFactory
import net.corda.schema.configuration.BootConfig.BOOT_CRYPTO
import net.corda.schema.configuration.ConfigKeys.CRYPTO_CONFIG
import net.corda.schema.configuration.ConfigKeys.FLOW_CONFIG
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.test.assertEquals
import kotlin.test.assertNull

class CryptoConfigUtilsTests {
    companion object {
        private lateinit var configFactory: SmartConfigFactory
        private lateinit var smartConfig: SmartConfig

        @JvmStatic
        @BeforeAll
        fun setup() {
            configFactory = SmartConfigFactory.create(
                ConfigFactory.parseString(
                    """
            ${SmartConfigFactory.SECRET_PASSPHRASE_KEY}=key
            ${SmartConfigFactory.SECRET_SALT_KEY}=salt
        """.trimIndent()
                )
            )
            smartConfig = createDefaultCryptoConfig(
                smartFactoryKey = KeyCredentials("key", "salt"),
                masterWrappingKey = KeyCredentials("master-passphrase", "master-salt")
            )
        }
    }

    @Test
    fun `Default config should have expected values`() {
        val config = smartConfig
        val connectionFactory = config.cryptoConnectionFactory()
        assertEquals(5, connectionFactory.expireAfterAccessMins)
        assertEquals(3, connectionFactory.maximumSize)
        val signingService = config.signingService()
        assertEquals(60, signingService.cache.expireAfterAccessMins)
        assertEquals(10000, signingService.cache.maximumSize)
        val hsmService = config.hsmService()
        assertEquals(3, hsmService.downstreamMaxAttempts)
        assertEquals(SOFT_HSM_ID, config.hsmId())
        assertThat(config.hsmMap()).hasSize(1)
        val softWorker = config.hsm()
        assertEquals("", softWorker.workerTopicSuffix)
        assertEquals(20000L, softWorker.retry.attemptTimeoutMills)
        assertEquals(3, softWorker.retry.maxAttempts)
        assertEquals(SOFT_HSM_SERVICE_NAME, softWorker.hsm.name)
        assertThat(softWorker.hsm.categories).hasSize(1)
        assertEquals("*", softWorker.hsm.categories[0].category)
        assertEquals(PrivateKeyPolicy.WRAPPED, softWorker.hsm.categories[0].policy)
        assertEquals(MasterKeyPolicy.UNIQUE, softWorker.hsm.masterKeyPolicy)
        assertNull(softWorker.hsm.masterKeyAlias)
        assertEquals(-1, softWorker.hsm.capacity)
        assertThat(softWorker.hsm.supportedSchemes).hasSize(8)
        assertThat(softWorker.hsm.supportedSchemes).contains(
            "CORDA.RSA",
            "CORDA.ECDSA.SECP256R1",
            "CORDA.ECDSA.SECP256K1",
            "CORDA.EDDSA.ED25519",
            "CORDA.X25519",
            "CORDA.SM2",
            "CORDA.GOST3410.GOST3411",
            "CORDA.SPHINCS-256"
        )
        val hsmCfg = softWorker.hsm.cfg
        assertEquals("CACHING", hsmCfg.getString("keyMap.name"))
        assertEquals(60, hsmCfg.getLong("keyMap.cache.expireAfterAccessMins"))
        assertEquals(1000, hsmCfg.getLong("keyMap.cache.maximumSize"))
        assertEquals("CACHING", hsmCfg.getString("wrappingKeyMap.name"))
        assertEquals("master-salt", hsmCfg.getString("wrappingKeyMap.salt"))
        assertEquals(
            "master-passphrase", hsmCfg.toConfigurationSecrets().getSecret(
                hsmCfg.getConfig("wrappingKeyMap.passphrase").root().unwrapped()
            )
        )
        assertEquals(60, hsmCfg.getLong("wrappingKeyMap.cache.expireAfterAccessMins"))
        assertEquals(1000, hsmCfg.getLong("wrappingKeyMap.cache.maximumSize"))
        assertEquals("DEFAULT", hsmCfg.getString("wrapping.name"))
        val opsBusProcessor = config.opsBusProcessor()
        assertEquals(3, opsBusProcessor.maxAttempts)
        assertEquals(1, opsBusProcessor.waitBetweenMills.size)
        assertEquals(200L, opsBusProcessor.waitBetweenMills[0])
        val flowBusProcessor = config.flowBusProcessor()
        assertEquals(3, flowBusProcessor.maxAttempts)
        assertEquals(1, flowBusProcessor.waitBetweenMills.size)
        assertEquals(200L, flowBusProcessor.waitBetweenMills[0])
        val hsmRegistrationBusProcessor = config.hsmRegistrationBusProcessor()
        assertEquals(3, hsmRegistrationBusProcessor.maxAttempts)
        assertEquals(1, hsmRegistrationBusProcessor.waitBetweenMills.size)
        assertEquals(200L, hsmRegistrationBusProcessor.waitBetweenMills[0])
    }

    @Test
    fun `Should add default values preserving existing config`() {
        val config = configFactory.create(
            ConfigFactory.parseMap(
                mapOf(
                    "instanceId" to 123,
                    BOOT_CRYPTO to mapOf(
                        "cryptoConnectionFactory.expireAfterAccessMins" to "480",
                        "signingService.cache.maximumSize" to "77",
                        "hsmService.downstreamMaxAttempts" to "11",
                        "hsmMap.SOFT.hsm.capacity" to "0"
                    )
                )
            )
        ).addDefaultBootCryptoConfig(
            fallbackMasterWrappingKey = KeyCredentials("soft-passphrase", "soft-salt")
        ).getConfig(BOOT_CRYPTO)
        val connectionFactory = config.cryptoConnectionFactory()
        assertEquals(480, connectionFactory.expireAfterAccessMins)
        assertEquals(3, connectionFactory.maximumSize)
        val signingService = config.signingService()
        assertEquals(60, signingService.cache.expireAfterAccessMins)
        assertEquals(77, signingService.cache.maximumSize)
        val hsmService = config.hsmService()
        assertEquals(11, hsmService.downstreamMaxAttempts)
        assertEquals(SOFT_HSM_ID, config.hsmId())
        assertThat(config.hsmMap()).hasSize(1)
        val softWorker = config.hsm()
        assertEquals("", softWorker.workerTopicSuffix)
        assertEquals(20000L, softWorker.retry.attemptTimeoutMills)
        assertEquals(3, softWorker.retry.maxAttempts)
        assertEquals(SOFT_HSM_SERVICE_NAME, softWorker.hsm.name)
        assertThat(softWorker.hsm.categories).hasSize(1)
        assertEquals("*", softWorker.hsm.categories[0].category)
        assertEquals(PrivateKeyPolicy.WRAPPED, softWorker.hsm.categories[0].policy)
        assertEquals(MasterKeyPolicy.UNIQUE, softWorker.hsm.masterKeyPolicy)
        assertNull(softWorker.hsm.masterKeyAlias)
        assertEquals(0, softWorker.hsm.capacity)
        assertThat(softWorker.hsm.supportedSchemes).hasSize(8)
        assertThat(softWorker.hsm.supportedSchemes).contains(
            "CORDA.RSA",
            "CORDA.ECDSA.SECP256R1",
            "CORDA.ECDSA.SECP256K1",
            "CORDA.EDDSA.ED25519",
            "CORDA.X25519",
            "CORDA.SM2",
            "CORDA.GOST3410.GOST3411",
            "CORDA.SPHINCS-256"
        )
        val hsmCfg = softWorker.hsm.cfg
        assertEquals("CACHING", hsmCfg.getString("keyMap.name"))
        assertEquals(60, hsmCfg.getLong("keyMap.cache.expireAfterAccessMins"))
        assertEquals(1000, hsmCfg.getLong("keyMap.cache.maximumSize"))
        assertEquals("CACHING", hsmCfg.getString("wrappingKeyMap.name"))
        assertEquals("soft-salt", hsmCfg.getString("wrappingKeyMap.salt"))
        assertEquals(
            "soft-passphrase", hsmCfg.toConfigurationSecrets().getSecret(
                hsmCfg.getConfig("wrappingKeyMap.passphrase").root().unwrapped()
            )
        )
        assertEquals(60, hsmCfg.getLong("wrappingKeyMap.cache.expireAfterAccessMins"))
        assertEquals(1000, hsmCfg.getLong("wrappingKeyMap.cache.maximumSize"))
        assertEquals("DEFAULT", hsmCfg.getString("wrapping.name"))
        val opsBusProcessor = config.opsBusProcessor()
        assertEquals(3, opsBusProcessor.maxAttempts)
        assertEquals(1, opsBusProcessor.waitBetweenMills.size)
        assertEquals(200L, opsBusProcessor.waitBetweenMills[0])
        val flowBusProcessor = config.flowBusProcessor()
        assertEquals(3, flowBusProcessor.maxAttempts)
        assertEquals(1, flowBusProcessor.waitBetweenMills.size)
        assertEquals(200L, flowBusProcessor.waitBetweenMills[0])
        val hsmRegistrationBusProcessor = config.hsmRegistrationBusProcessor()
        assertEquals(3, hsmRegistrationBusProcessor.maxAttempts)
        assertEquals(1, hsmRegistrationBusProcessor.waitBetweenMills.size)
        assertEquals(200L, hsmRegistrationBusProcessor.waitBetweenMills[0])
    }

    @Test
    fun `Should be able to get crypto config from the map`() {
        val map = mapOf(
            FLOW_CONFIG to configFactory.create(ConfigFactory.empty()),
            CRYPTO_CONFIG to smartConfig
        )
        val result = map.toCryptoConfig()
        assertSame(smartConfig, result)
    }

    @Test
    fun `Should throw IllegalStateException when crypto key is not found in the map`() {
        val map = mapOf(
            FLOW_CONFIG to configFactory.create(ConfigFactory.empty())
        )
        assertThrows<IllegalStateException> {
            map.toCryptoConfig()
        }
    }

    @Test
    fun `Should be able to get signing service config`() {
        val config = smartConfig.signingService()
        assertEquals(60, config.cache.expireAfterAccessMins)
        assertEquals(10000, config.cache.maximumSize)
    }

    @Test
    fun `Should be able to get CryptoHHSM service config`() {
        val config = smartConfig.hsmService()
        assertEquals(3, config.downstreamMaxAttempts)
    }

    @Test
    fun `Should throw IllegalStateException when signing service is missing`() {
        val config = configFactory.create(ConfigFactory.empty())
        assertThrows<IllegalStateException> {
            config.signingService()
        }
    }

    @Test
    fun `Should throw IllegalStateException when HSM service is missing`() {
        val config = configFactory.create(ConfigFactory.empty())
        assertThrows<IllegalStateException> {
            config.hsmService()
        }
    }

    @Test
    fun `Should throw IllegalStateException when ops operations are missing`() {
        val config = configFactory.create(ConfigFactory.empty())
        assertThrows<IllegalStateException> {
            config.opsBusProcessor()
        }
    }

    @Test
    fun `Should throw IllegalStateException when flow ops operations are missing`() {
        val config = configFactory.create(ConfigFactory.empty())
        assertThrows<IllegalStateException> {
            config.flowBusProcessor()
        }
    }

    @Test
    fun `Should throw IllegalStateException when hsm registration operations are missing`() {
        val config = configFactory.create(ConfigFactory.empty())
        assertThrows<IllegalStateException> {
            config.opsBusProcessor()
        }
    }

    @Test
    fun `CryptoConnectionsFactoryConfig should throw IllegalStateException when is empty`() {
        val config = CryptoConnectionsFactoryConfig(
            configFactory.create(ConfigFactory.empty())
        )
        assertThrows<IllegalStateException> {
            config.expireAfterAccessMins
        }
        assertThrows<IllegalStateException> {
            config.maximumSize
        }
    }

    @Test
    fun `CryptoSigningServiceConfig should throw IllegalStateException when is empty`() {
        val config = CryptoSigningServiceConfig(
            configFactory.create(ConfigFactory.empty())
        )
        assertThrows<IllegalStateException> {
            config.cache
        }
        val cacheConfig = CryptoSigningServiceConfig.CacheConfig(
            configFactory.create(ConfigFactory.empty())
        )
        assertThrows<IllegalStateException> {
            cacheConfig.expireAfterAccessMins
        }
        assertThrows<IllegalStateException> {
            cacheConfig.maximumSize
        }
    }

    @Test
    fun `CryptoHSMConfig should throw IllegalStateException when is empty`() {
        val config = CryptoHSMConfig(configFactory.create(ConfigFactory.empty()))
        assertThrows<IllegalStateException> {
            config.retry
        }
        assertThrows<IllegalStateException> {
            config.workerTopicSuffix
        }
        assertThrows<IllegalStateException> {
            config.hsm
        }
        val categoryConfig = CryptoHSMConfig.CategoryConfig(configFactory.create(ConfigFactory.empty()))
        assertThrows<IllegalStateException> {
            categoryConfig.category
        }
        assertThrows<IllegalStateException> {
            categoryConfig.policy
        }
        val retryConfig = CryptoHSMConfig.RetryConfig(configFactory.create(ConfigFactory.empty()))
        assertThrows<IllegalStateException> {
            retryConfig.maxAttempts
        }
        assertThrows<IllegalStateException> {
            retryConfig.attemptTimeoutMills
        }
        val hsmConfig = CryptoHSMConfig.HSMConfig(configFactory.create(ConfigFactory.empty()))
        assertThrows<IllegalStateException> {
            hsmConfig.name
        }
        assertThrows<IllegalStateException> {
            hsmConfig.categories
        }
        assertThrows<IllegalStateException> {
            hsmConfig.capacity
        }
        assertThrows<IllegalStateException> {
            hsmConfig.supportedSchemes
        }
        assertThrows<IllegalStateException> {
            hsmConfig.masterKeyPolicy
        }
        assertThrows<IllegalStateException> {
            hsmConfig.cfg
        }
    }

    @Test
    fun `CryptoSoftPersistenceConfig should throw IllegalStateException when is empty`() {
        val config = CryptoHSMServiceConfig(
            configFactory.create(ConfigFactory.empty())
        )
        assertThrows<IllegalStateException> {
            config.downstreamMaxAttempts
        }
        val cacheConfig = CryptoHSMServiceConfig.CacheConfig(
            configFactory.create(ConfigFactory.empty())
        )
        assertThrows<IllegalStateException> {
            cacheConfig.expireAfterAccessMins
        }
        assertThrows<IllegalStateException> {
            cacheConfig.maximumSize
        }
    }

    @Test
    fun `BusProcessorConfig should throw IllegalStateException when maxAttempts is empty`() {
        val config = CryptoBusProcessorConfig(
            configFactory.create(ConfigFactory.empty())
        )
        assertThrows<IllegalStateException> {
            config.maxAttempts
        }
    }

    @Test
    fun `BusProcessorConfig should throw IllegalStateException when waitBetweenMills is empty`() {
        val config = CryptoBusProcessorConfig(
            configFactory.create(ConfigFactory.empty())
        )
        assertThrows<IllegalStateException> {
            config.waitBetweenMills
        }
    }

    /*
    @Test
    fun `Should add default crypto config with fallback credentials`() {
        val config = configFactory.create(
            ConfigFactory.parseMap(
                mapOf(
                    "instanceId" to 123,
                    BOOT_CRYPTO to emptyMap<String, Any>()
                )
            )
        ).addDefaultBootCryptoConfig(
            fallbackMasterWrappingKey = KeyCredentials("soft-passphrase", "soft-salt")
        )
        val cryptoConfig = config.getConfig(BOOT_CRYPTO)
        val testEncryptor = AesEncryptor(
            AesKey.derive(
                passphrase = "root-passphrase",
                salt = "root-salt"
            )
        )
        assertEquals(testEncryptor, encryptorFromConfig)
        val sofPersistence = cryptoConfig.softPersistence()
        assertEquals(240, sofPersistence.expireAfterAccessMins)
        assertEquals(1000, sofPersistence.maximumSize)
        assertEquals("soft-salt", sofPersistence.salt)
        assertEquals("soft-passphrase", sofPersistence.passphrase)
        assertEquals(1, sofPersistence.maxAttempts)
        assertEquals(20000, sofPersistence.attemptTimeoutMills)
        val signingPersistence = cryptoConfig.signingPersistence()
        assertEquals(90, signingPersistence.keysExpireAfterAccessMins)
        assertEquals(20, signingPersistence.keyNumberLimit)
        assertEquals(120, signingPersistence.vnodesExpireAfterAccessMins)
        assertEquals(100, signingPersistence.vnodeNumberLimit)
        assertEquals(15, signingPersistence.connectionsExpireAfterAccessMins)
        assertEquals(2, signingPersistence.connectionNumberLimit)
        val hsmPersistence = cryptoConfig.hsmService()
        assertEquals(240, hsmPersistence.expireAfterAccessMins)
        assertEquals(1000, hsmPersistence.maximumSize)
        assertEquals(3, hsmPersistence.downstreamMaxAttempts)
        assertTrue(config.hasPath("instanceId"))
    }

    @Test
    fun `Should add default crypto config with provided credentials`() {
        val config = configFactory.create(
            ConfigFactory.parseMap(
                mapOf(
                    "instanceId" to 123,
                    BOOT_CRYPTO to mapOf(
                        "rootKey.passphrase" to "p1",
                        "rootKey.salt" to "s1",
                        "softPersistence.passphrase" to "p2",
                        "softPersistence.salt" to "s2"
                    )
                )
            )
        ).addDefaultBootCryptoConfig(
            fallbackCryptoRootKey = KeyCredentials("root-passphrase", "root-salt"),
            fallbackMasterWrappingKey = KeyCredentials("soft-passphrase", "soft-salt")
        )
        val cryptoConfig = config.getConfig(BOOT_CRYPTO)
        val encryptorFromConfig = cryptoConfig.rootEncryptor()
        val testEncryptor = AesEncryptor(
            AesKey.derive(
                passphrase = "p1",
                salt = "s1"
            )
        )
        assertEquals(testEncryptor, encryptorFromConfig)
        val sofPersistence = cryptoConfig.softPersistence()
        assertEquals(240, sofPersistence.expireAfterAccessMins)
        assertEquals(1000, sofPersistence.maximumSize)
        assertEquals("s2", sofPersistence.salt)
        assertEquals("p2", sofPersistence.passphrase)
        assertEquals(1, sofPersistence.maxAttempts)
        assertEquals(20000, sofPersistence.attemptTimeoutMills)
        val signingPersistence = cryptoConfig.signingPersistence()
        assertEquals(90, signingPersistence.keysExpireAfterAccessMins)
        assertEquals(20, signingPersistence.keyNumberLimit)
        assertEquals(120, signingPersistence.vnodesExpireAfterAccessMins)
        assertEquals(100, signingPersistence.vnodeNumberLimit)
        assertEquals(15, signingPersistence.connectionsExpireAfterAccessMins)
        assertEquals(2, signingPersistence.connectionNumberLimit)
        val hsmPersistence = cryptoConfig.hsmService()
        assertEquals(240, hsmPersistence.expireAfterAccessMins)
        assertEquals(1000, hsmPersistence.maximumSize)
        assertEquals(3, hsmPersistence.downstreamMaxAttempts)
        assertTrue(config.hasPath("instanceId"))
    }

    @Test
    fun `Should add default crypto config with provided salt only`() {
        val config = configFactory.create(
            ConfigFactory.parseMap(
                mapOf(
                    "instanceId" to 123,
                    BOOT_CRYPTO to mapOf(
                        "rootKey.salt" to "s1",
                        "softPersistence.salt" to "s2"
                    )
                )
            )
        ).addDefaultBootCryptoConfig(
            fallbackCryptoRootKey = KeyCredentials("root-passphrase", "root-salt"),
            fallbackMasterWrappingKey = KeyCredentials("soft-passphrase", "soft-salt")
        )
        val cryptoConfig = config.getConfig(BOOT_CRYPTO)
        val encryptorFromConfig = cryptoConfig.rootEncryptor()
        val testEncryptor = AesEncryptor(
            AesKey.derive(
                passphrase = "root-passphrase",
                salt = "s1"
            )
        )
        assertEquals(testEncryptor, encryptorFromConfig)
        val sofPersistence = cryptoConfig.softPersistence()
        assertEquals(240, sofPersistence.expireAfterAccessMins)
        assertEquals(1000, sofPersistence.maximumSize)
        assertEquals("s2", sofPersistence.salt)
        assertEquals("soft-passphrase", sofPersistence.passphrase)
        assertEquals(1, sofPersistence.maxAttempts)
        assertEquals(20000, sofPersistence.attemptTimeoutMills)
        val signingPersistence = cryptoConfig.signingPersistence()
        assertEquals(90, signingPersistence.keysExpireAfterAccessMins)
        assertEquals(20, signingPersistence.keyNumberLimit)
        assertEquals(120, signingPersistence.vnodesExpireAfterAccessMins)
        assertEquals(100, signingPersistence.vnodeNumberLimit)
        assertEquals(15, signingPersistence.connectionsExpireAfterAccessMins)
        assertEquals(2, signingPersistence.connectionNumberLimit)
        val hsmPersistence = cryptoConfig.hsmService()
        assertEquals(240, hsmPersistence.expireAfterAccessMins)
        assertEquals(1000, hsmPersistence.maximumSize)
        assertEquals(3, hsmPersistence.downstreamMaxAttempts)
        assertTrue(config.hasPath("instanceId"))
    }

    @Test
    fun `Should add default crypto config with provided passphrase only`() {
        val config = configFactory.create(
            ConfigFactory.parseMap(
                mapOf(
                    "instanceId" to 123,
                    BOOT_CRYPTO to mapOf(
                        "rootKey.passphrase" to "p1",
                        "softPersistence.passphrase" to "p2"
                    )
                )
            )
        ).addDefaultBootCryptoConfig(
            fallbackCryptoRootKey = KeyCredentials("root-passphrase", "root-salt"),
            fallbackMasterWrappingKey = KeyCredentials("soft-passphrase", "soft-salt")
        )
        val cryptoConfig = config.getConfig(BOOT_CRYPTO)
        val encryptorFromConfig = cryptoConfig.rootEncryptor()
        val testEncryptor = AesEncryptor(
            AesKey.derive(
                passphrase = "p1",
                salt = "root-salt"
            )
        )
        assertEquals(testEncryptor, encryptorFromConfig)
        val sofPersistence = cryptoConfig.softPersistence()
        assertEquals(240, sofPersistence.expireAfterAccessMins)
        assertEquals(1000, sofPersistence.maximumSize)
        assertEquals("soft-salt", sofPersistence.salt)
        assertEquals("p2", sofPersistence.passphrase)
        assertEquals(1, sofPersistence.maxAttempts)
        assertEquals(20000, sofPersistence.attemptTimeoutMills)
        val signingPersistence = cryptoConfig.signingPersistence()
        assertEquals(90, signingPersistence.keysExpireAfterAccessMins)
        assertEquals(20, signingPersistence.keyNumberLimit)
        assertEquals(120, signingPersistence.vnodesExpireAfterAccessMins)
        assertEquals(100, signingPersistence.vnodeNumberLimit)
        assertEquals(15, signingPersistence.connectionsExpireAfterAccessMins)
        assertEquals(2, signingPersistence.connectionNumberLimit)
        val hsmPersistence = cryptoConfig.hsmService()
        assertEquals(240, hsmPersistence.expireAfterAccessMins)
        assertEquals(1000, hsmPersistence.maximumSize)
        assertEquals(3, hsmPersistence.downstreamMaxAttempts)
        assertTrue(config.hasPath("instanceId"))
    }

    @Test
    fun `Should add default crypto config with preserving provided data`() {
        val config = configFactory.create(
            ConfigFactory.parseMap(
                mapOf(
                    "instanceId" to 123,
                    BOOT_CRYPTO to mapOf(
                        "rootKey.passphrase" to "p1",
                        "rootKey.salt" to "s1",
                        "softPersistence.passphrase" to "p2",
                        "softPersistence.salt" to "s2",
                        "softPersistence.maximumSize" to "77",
                        "signingPersistence.keysExpireAfterAccessMins" to "42",
                        "signingPersistence.keyNumberLimit" to "21",
                        "signingPersistence.vnodesExpireAfterAccessMins" to "127",
                        "signingPersistence.vnodeNumberLimit" to "55",
                        "signingPersistence.connectionsExpireAfterAccessMins" to "17",
                        "signingPersistence.connectionNumberLimit" to "3",
                        "hsmPersistence.expireAfterAccessMins" to "11",
                        "hsmPersistence.maximumSize" to 222,
                        "hsmPersistence.downstreamMaxAttempts" to 17
                    )
                )
            )
        ).addDefaultBootCryptoConfig(
            fallbackCryptoRootKey = KeyCredentials("root-passphrase", "root-salt"),
            fallbackMasterWrappingKey = KeyCredentials("soft-passphrase", "soft-salt")
        )
        val cryptoConfig = config.getConfig(BOOT_CRYPTO)
        val encryptorFromConfig = cryptoConfig.rootEncryptor()
        val testEncryptor = AesEncryptor(
            AesKey.derive(
                passphrase = "p1",
                salt = "s1"
            )
        )
        assertEquals(testEncryptor, encryptorFromConfig)
        val sofPersistence = cryptoConfig.softPersistence()
        assertEquals(240, sofPersistence.expireAfterAccessMins)
        assertEquals(77, sofPersistence.maximumSize)
        assertEquals("s2", sofPersistence.salt)
        assertEquals("p2", sofPersistence.passphrase)
        assertEquals(1, sofPersistence.maxAttempts)
        assertEquals(20000, sofPersistence.attemptTimeoutMills)
        val signingPersistence = cryptoConfig.signingPersistence()
        assertEquals(42, signingPersistence.keysExpireAfterAccessMins)
        assertEquals(21, signingPersistence.keyNumberLimit)
        assertEquals(127, signingPersistence.vnodesExpireAfterAccessMins)
        assertEquals(55, signingPersistence.vnodeNumberLimit)
        assertEquals(17, signingPersistence.connectionsExpireAfterAccessMins)
        assertEquals(3, signingPersistence.connectionNumberLimit)
        val hsmPersistence = cryptoConfig.hsmService()
        assertEquals(11, hsmPersistence.expireAfterAccessMins)
        assertEquals(222, hsmPersistence.maximumSize)
        assertEquals(17, hsmPersistence.downstreamMaxAttempts)
        assertTrue(config.hasPath("instanceId"))
    }

     */
}