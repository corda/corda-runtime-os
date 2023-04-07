package net.corda.crypto.config.impl

import com.typesafe.config.ConfigFactory
import com.typesafe.config.ConfigRenderOptions
import net.corda.libs.configuration.SmartConfigFactory
import net.corda.libs.configuration.secret.EncryptionSecretsServiceFactory
import net.corda.libs.configuration.validation.impl.ConfigurationValidatorFactoryImpl
import net.corda.schema.configuration.ConfigKeys.CRYPTO_CONFIG
import net.corda.schema.configuration.ConfigKeys.FLOW_CONFIG
import net.corda.v5.base.versioning.Version
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
        val signingService = config.signingService()
        assertEquals(60, signingService.cache.expireAfterAccessMins)
        assertEquals(10000, signingService.cache.maximumSize)
        assertThat(config.hsmMap()).hasSize(1)
        val softWorker = config.hsm()
        assertEquals(20000L, softWorker.retry.attemptTimeoutMills)
        assertEquals(3, softWorker.retry.maxAttempts)
        assertThat(softWorker.categories).hasSize(1)
        assertEquals("*", softWorker.categories[0].category)
        assertEquals(PrivateKeyPolicy.WRAPPED, softWorker.categories[0].policy)
        assertEquals(MasterKeyPolicy.UNIQUE, softWorker.masterKeyPolicy)
        assertNull(softWorker.masterKeyAlias)
        assertEquals(-1, softWorker.capacity)
        assertThat(softWorker.supportedSchemes).hasSize(8)
        assertThat(softWorker.supportedSchemes).contains(
            "CORDA.RSA",
            "CORDA.ECDSA.SECP256R1",
            "CORDA.ECDSA.SECP256K1",
            "CORDA.EDDSA.ED25519",
            "CORDA.X25519",
            "CORDA.SM2",
            "CORDA.GOST3410.GOST3411",
            "CORDA.SPHINCS-256"
        )
        val hsmCfg = softWorker.cfg
        val wrappingKey1 = hsmCfg.getConfigList("wrappingKeys")[0]
        assertEquals("master-salt", wrappingKey1.getString("salt"))
        assertEquals("master-passphrase", wrappingKey1.getString("passphrase"))
        assertEquals("root1", wrappingKey1.getString("alias"))
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
    fun `Crypto config should validate`() {
        val validator = ConfigurationValidatorFactoryImpl().createConfigValidator()
        val config = createDefaultCryptoConfig("pass", "salt")
        val configJSON = config.root().render(ConfigRenderOptions.defaults())
        assertThat(configJSON.contains("passphrase"))
        validator.validate(CRYPTO_CONFIG, Version(1, 0), config, false)
    }

    @Test
    fun `Test config should have expected values`() {
        val config = createDefaultCryptoConfig("pass", "salt")
        val signingService = config.signingService()
        assertEquals(60, signingService.cache.expireAfterAccessMins)
        assertEquals(10000, signingService.cache.maximumSize)
        assertThat(config.hsmMap()).hasSize(1)
        val softWorker = config.hsm()
        assertEquals(20000L, softWorker.retry.attemptTimeoutMills)
        assertEquals(3, softWorker.retry.maxAttempts)
        assertThat(softWorker.categories).hasSize(1)
        assertEquals("*", softWorker.categories[0].category)
        assertEquals(PrivateKeyPolicy.WRAPPED, softWorker.categories[0].policy)
        assertEquals(MasterKeyPolicy.UNIQUE, softWorker.masterKeyPolicy)
        assertNull(softWorker.masterKeyAlias)
        assertEquals(-1, softWorker.capacity)
        assertThat(softWorker.supportedSchemes).hasSize(8)
        assertThat(softWorker.supportedSchemes).contains(
            "CORDA.RSA",
            "CORDA.ECDSA.SECP256R1",
            "CORDA.ECDSA.SECP256K1",
            "CORDA.EDDSA.ED25519",
            "CORDA.X25519",
            "CORDA.SM2",
            "CORDA.GOST3410.GOST3411",
            "CORDA.SPHINCS-256"
        )
        val hsmCfg = softWorker.cfg
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
        val config = SmartConfigFactory.createWithoutSecurityServices().create(
            createDefaultCryptoConfig("master-passphrase", "master-salt")
        )
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
        val config = createDefaultCryptoConfig(
            "master-passphrase", "master-salt"
        ).signingService()
        assertEquals(60, config.cache.expireAfterAccessMins)
        assertEquals(10000, config.cache.maximumSize)
    }

    @Test
    fun `Should throw IllegalStateException when signing service is missing`() {
        val config = configFactory.create(ConfigFactory.empty())
        assertThrows<IllegalStateException> {
            config.signingService()
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
    fun `CryptoSigningServiceConfig should throw IllegalStateException when is empty`() {
        assertThrows<IllegalStateException> {
            CryptoSigningServiceConfig(
                configFactory.create(ConfigFactory.empty())
            )
        }
        val cacheConfig = CacheConfig(
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
        assertThrows<IllegalStateException> {
            config.categories
        }
        assertThrows<IllegalStateException> {
            config.capacity
        }
        assertThrows<IllegalStateException> {
            config.supportedSchemes
        }
        assertThrows<IllegalStateException> {
            config.masterKeyPolicy
        }
        assertThrows<IllegalStateException> {
            config.cfg
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
    fun `hsmMap should throw IllegalStateException if value is not found`() {
        val config = configFactory.create(ConfigFactory.empty())
        assertThrows<IllegalStateException> {
            config.hsmMap()
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