package net.corda.crypto.config.impl

import com.typesafe.config.ConfigFactory
import com.typesafe.config.ConfigObject
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
        val config = createDefaultCryptoConfig(listOf(KeyDerivationParameters("master-passphrase", "master-salt")))
        val signingService = config.signingService()
        assertEquals(60, signingService.cache.expireAfterAccessMins)
        assertEquals(10000, signingService.cache.maximumSize)
        val softWorker = config.hsm()
        assertEquals(20000L, softWorker.retrying.attemptTimeoutMills)
        assertEquals(3, softWorker.retrying.maxAttempts)
        val wrappingKey1 = softWorker.wrappingKeys[0] as ConfigObject
        assertEquals("master-salt", wrappingKey1["salt"]!!.unwrapped())
        assertEquals("master-passphrase", wrappingKey1["passphrase"]!!.unwrapped())
        assertEquals("root1", wrappingKey1["alias"]!!.unwrapped())
        val opsBusProcessor = config.retrying()
        assertEquals(3, opsBusProcessor.maxAttempts)
        assertEquals(1, opsBusProcessor.waitBetweenMills.size)
        assertEquals(200L, opsBusProcessor.waitBetweenMills[0])
        val flowBusProcessor = config.retrying()
        assertEquals(3, flowBusProcessor.maxAttempts)
        assertEquals(1, flowBusProcessor.waitBetweenMills.size)
        assertEquals(200L, flowBusProcessor.waitBetweenMills[0])
        val hsmRegistrationBusProcessor = config.retrying()
        assertEquals(3, hsmRegistrationBusProcessor.maxAttempts)
        assertEquals(1, hsmRegistrationBusProcessor.waitBetweenMills.size)
        assertEquals(200L, hsmRegistrationBusProcessor.waitBetweenMills[0])
    }

    @Test
    fun `Crypto config should validate`() {
        val validator = ConfigurationValidatorFactoryImpl().createConfigValidator()
        val config = createDefaultCryptoConfig(listOf(KeyDerivationParameters("pass", "salt")))
        val configJSON = config.root().render(ConfigRenderOptions.defaults())
        assertThat(configJSON.contains("passphrase"))
        validator.validate(CRYPTO_CONFIG, Version(1, 0), config, true)
    }

    @Test
    fun `Test config should have expected values`() {
        val config = createDefaultCryptoConfig(listOf(KeyDerivationParameters("pass", "salt")))
        val signingService = config.signingService()
        assertEquals(60, signingService.cache.expireAfterAccessMins)
        assertEquals(10000, signingService.cache.maximumSize)
        val softWorker = config.hsm()
        assertEquals(20000L, softWorker.retrying.attemptTimeoutMills)
        assertEquals(3, softWorker.retrying.maxAttempts)
        val opsBusProcessor = config.retrying()
        assertEquals(3, opsBusProcessor.maxAttempts)
        assertEquals(1, opsBusProcessor.waitBetweenMills.size)
        assertEquals(200L, opsBusProcessor.waitBetweenMills[0])
        val flowBusProcessor = config.retrying()
        assertEquals(3, flowBusProcessor.maxAttempts)
        assertEquals(1, flowBusProcessor.waitBetweenMills.size)
        assertEquals(200L, flowBusProcessor.waitBetweenMills[0])
        val hsmRegistrationBusProcessor = config.retrying()
        assertEquals(3, hsmRegistrationBusProcessor.maxAttempts)
        assertEquals(1, hsmRegistrationBusProcessor.waitBetweenMills.size)
        assertEquals(200L, hsmRegistrationBusProcessor.waitBetweenMills[0])
    }

    @Test
    fun `Should be able to get crypto config from the map of configs`() {
        val config = SmartConfigFactory.createWithoutSecurityServices().create(
            createDefaultCryptoConfig(listOf(KeyDerivationParameters("master-passphrase", "master-salt")))
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
            listOf(KeyDerivationParameters("master-passphrase", "master-salt"))
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
            config.retrying()
        }
    }

    @Test
    fun `Should throw IllegalStateException when flow ops operations are missing`() {
        val config = configFactory.create(ConfigFactory.empty())
        assertThrows<IllegalStateException> {
            config.retrying()
        }
    }

    @Test
    fun `Should throw IllegalStateException when hsm registration operations are missing`() {
        val config = configFactory.create(ConfigFactory.empty())
        assertThrows<IllegalStateException> {
            config.retrying()
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
            config.retrying
        }
        val retryConfig = CryptoHSMConfig.RetryConfig(configFactory.create(ConfigFactory.empty()))
        assertThrows<IllegalStateException> {
            retryConfig.maxAttempts
        }
        assertThrows<IllegalStateException> {
            retryConfig.attemptTimeoutMills
        }
        assertThrows<IllegalStateException> {
            config.wrappingKeys
        }
        assertThrows<IllegalStateException> {
            config.defaultWrappingKey
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
        val config = RetryingConfig(
            configFactory.create(ConfigFactory.empty())
        )
        assertThrows<IllegalStateException> {
            config.maxAttempts
        }
    }

    @Test
    fun `BusProcessorConfig should throw IllegalStateException when waitBetweenMills is empty`() {
        val config = RetryingConfig(
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
            config.retrying()
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