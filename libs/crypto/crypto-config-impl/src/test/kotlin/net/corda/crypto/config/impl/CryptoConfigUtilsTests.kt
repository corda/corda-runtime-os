package net.corda.crypto.config.impl

import com.typesafe.config.ConfigFactory
import net.corda.crypto.core.CryptoConsts.SOFT_HSM_ID
import net.corda.crypto.core.CryptoConsts.SOFT_HSM_SERVICE_NAME
import net.corda.crypto.core.aes.KeyCredentials
import net.corda.libs.configuration.SmartConfigFactory
import net.corda.libs.configuration.secret.EncryptionSecretsServiceFactory
import net.corda.schema.configuration.ConfigKeys.CRYPTO_CONFIG
import net.corda.schema.configuration.ConfigKeys.FLOW_CONFIG
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertNull

class CryptoConfigUtilsTests {
    companion object {
        private lateinit var configFactory: SmartConfigFactory

        @JvmStatic
        @BeforeAll
        fun setup() {
            configFactory = SmartConfigFactory.createWith(
                ConfigFactory.parseString(
                    """
            ${EncryptionSecretsServiceFactory.SECRET_PASSPHRASE_KEY}=key
            ${EncryptionSecretsServiceFactory.SECRET_SALT_KEY}=salt
        """.trimIndent()
                ),
                listOf(EncryptionSecretsServiceFactory())
            )
        }
    }

    @Test
    fun `Default config should have expected values`() {
        val config = createDefaultCryptoConfig("master-passphrase", "master-salt")
        val connectionFactory = config.cryptoConnectionFactory()
        assertEquals(5, connectionFactory.expireAfterAccessMins)
        assertEquals(3, connectionFactory.maximumSize)
        val signingService = config.signingService()
        assertEquals(60, signingService.cache.expireAfterAccessMins)
        assertEquals(10000, signingService.cache.maximumSize)
        val hsmService = config.hsmService()
        assertEquals(3, hsmService.downstreamMaxAttempts)
        assertThat(config.hsmMap()).hasSize(1)
        val softWorker = config.hsm(SOFT_HSM_ID)
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
    fun `Test config should have expected values`() {
        val config = createTestCryptoConfig(
            KeyCredentials("pass", "salt")
        )
        val connectionFactory = config.cryptoConnectionFactory()
        assertEquals(5, connectionFactory.expireAfterAccessMins)
        assertEquals(3, connectionFactory.maximumSize)
        val signingService = config.signingService()
        assertEquals(60, signingService.cache.expireAfterAccessMins)
        assertEquals(10000, signingService.cache.maximumSize)
        val hsmService = config.hsmService()
        assertEquals(3, hsmService.downstreamMaxAttempts)
        assertThat(config.hsmMap()).hasSize(1)
        val softWorker = config.hsm(SOFT_HSM_ID)
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
        assertThat(hsmCfg.getString("wrappingKeyMap.salt")).isNotBlank
        assertThat(hsmCfg.toConfigurationSecrets().getSecret(
                hsmCfg.getConfig("wrappingKeyMap.passphrase").root().unwrapped()
            )
        ).isNotBlank
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
    fun `Should be able to get crypto config from the map of configs`() {
        val config = SmartConfigFactory.createWithoutSecurityServices()createDefaultCryptoConfig("master-passphrase", "master-salt")
        val map = mapOf(
            FLOW_CONFIG to configFactory.create(ConfigFactory.empty()),
            CRYPTO_CONFIG to config
        )
        val result = map.toCryptoConfig()
        assertSame(config, result)
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
        val config = configFactory.createDefaultCryptoConfig(
            KeyCredentials("master-passphrase", "master-salt")
        ).signingService()
        assertEquals(60, config.cache.expireAfterAccessMins)
        assertEquals(10000, config.cache.maximumSize)
    }

    @Test
    fun `Should be able to get CryptoHSM service config`() {
        val config = configFactory.createDefaultCryptoConfig(
            KeyCredentials("master-passphrase", "master-salt")
        ).hsmService()
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

    @Test
    fun `hsmRegistrationBusProcessor should throw IllegalStateException if value is not found`() {
        val config = configFactory.create(ConfigFactory.empty())
        assertThrows<IllegalStateException> {
            config.hsmRegistrationBusProcessor()
        }
    }

    @Test
    fun `cryptoConnectionFactory should throw IllegalStateException if value is not found`() {
        val config = configFactory.create(ConfigFactory.empty())
        assertThrows<IllegalStateException> {
            config.cryptoConnectionFactory()
        }
    }

    @Test
    fun `hsmMap should throw IllegalStateException if value is not found`() {
        val config = configFactory.create(ConfigFactory.empty())
        assertThrows<IllegalStateException> {
            config.hsmMap()
        }
    }

    @Test
    fun `hsm(id) should throw IllegalStateException if id is not found`() {
        val config = configFactory.createDefaultCryptoConfig(
            KeyCredentials("master-passphrase", "master-salt")
        )
        assertThrows<IllegalStateException> {
            config.hsm(UUID.randomUUID().toString())
        }
    }

    @Test
    fun `Should create bootstrap config`() {
        val config = createCryptoBootstrapParamsMap(
            "hsm-id-1"
        )
        assertThat(config).hasSize(1)
        assertThat(config).containsEntry("hsmId", "hsm-id-1")
    }

    @Test
    fun `Should get bootstrap HSM id`() {
        val config = configFactory.create(ConfigFactory.parseMap(createCryptoBootstrapParamsMap(
            "hsm-id-1"
        )))
        val id = config.bootstrapHsmId()
        assertThat(id).isEqualTo("hsm-id-1")
    }

    @Test
    fun `bootstrapHsmId should throw IllegalStateException if value is not found`() {
        val config = configFactory.create(ConfigFactory.empty())
        assertThrows<IllegalStateException> {
            config.bootstrapHsmId()
        }
    }
}