package net.corda.crypto.impl.config

import com.typesafe.config.ConfigFactory
import com.typesafe.config.ConfigValueFactory
import java.util.UUID
import kotlin.test.assertEquals
import net.corda.crypto.core.aes.AesEncryptor
import net.corda.crypto.core.aes.AesKey
import net.corda.crypto.core.aes.KeyCredentials
import net.corda.libs.configuration.SmartConfig
import net.corda.libs.configuration.SmartConfigFactory
import net.corda.schema.configuration.BootConfig.BOOT_CRYPTO
import net.corda.schema.configuration.ConfigKeys.CRYPTO_CONFIG
import net.corda.schema.configuration.ConfigKeys.FLOW_CONFIG
import net.corda.v5.crypto.exceptions.CryptoConfigurationException
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

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
                cryptoRootKey = KeyCredentials("root-passphrase", "root-salt"),
                softKey = KeyCredentials("soft-passphrase", "soft-salt")
            )
        }
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
    fun `Should throw CryptoConfigurationException when crypto key is not found in the map`() {
        val map = mapOf(
            FLOW_CONFIG to configFactory.create(ConfigFactory.empty())
        )
        assertThrows<CryptoConfigurationException> {
            map.toCryptoConfig()
        }
    }

    @Test
    fun `Should be able to get root encryptor with deterministic key`() {
        val encryptorFromConfig = smartConfig.rootEncryptor()
        val testEncryptor = AesEncryptor(
            AesKey.derive(
                passphrase = "root-passphrase",
                salt = "root-salt"
            )
        )
        val original = UUID.randomUUID().toString().toByteArray()
        val encrypted = encryptorFromConfig.encrypt(original)
        val decrypted = testEncryptor.decrypt(encrypted)
        assertArrayEquals(original, decrypted)
    }

    @Test
    fun `Should throw CryptoConfigurationException to get default root encryptor when passphrase is missing`() {
        val config = configFactory.create(
            ConfigFactory.empty()
                .withValue("rootKey.salt", ConfigValueFactory.fromAnyRef("root-salt"))
        )
        assertThrows<CryptoConfigurationException> {
            config.rootEncryptor()
        }
    }

    @Test
    fun `Should throw CryptoConfigurationException to get default root encryptor when salt is missing`() {
        val config = configFactory.create(
            ConfigFactory.empty()
                .withValue(
                    "rootKey.passphrase", ConfigValueFactory.fromMap(
                        configFactory.makeSecret("root-passphrase").root().unwrapped()
                    )
                )
        )
        assertThrows<CryptoConfigurationException> {
            config.rootEncryptor()
        }
    }

    @Test
    fun `Should be able to get CryptoSoftPersistenceConfig and its properties`() {
        val config = smartConfig.softPersistence()
        assertEquals(240, config.expireAfterAccessMins)
        assertEquals(1000, config.maximumSize)
        assertEquals("soft-salt", config.salt)
        assertEquals("soft-passphrase", config.passphrase)
        assertEquals(0, config.maxAttempts)
        assertEquals(20000, config.attemptTimeoutMills)
    }

    @Test
    fun `Should be able to get CryptoSigningPersistenceConfigTests and its properties`() {
        val config = smartConfig.signingPersistence()
        assertEquals(90, config.keysExpireAfterAccessMins)
        assertEquals(20, config.keyNumberLimit)
    }

    @Test
    fun `Should be able to get CryptoHSMPersistenceConfigTests and its properties`() {
        val config = smartConfig.hsmPersistence()
        assertEquals(240, config.expireAfterAccessMins)
        assertEquals(1000, config.maximumSize)
        assertEquals(3, config.downstreamMaxAttempts)
    }

    @Test
    fun `Should throw CryptoConfigurationException when soft persistence is missing`() {
        val config = configFactory.create(ConfigFactory.empty())
        assertThrows<CryptoConfigurationException> {
            config.softPersistence()
        }
    }

    @Test
    fun `Should throw CryptoConfigurationException when signing persistence is missing`() {
        val config = configFactory.create(ConfigFactory.empty())
        assertThrows<CryptoConfigurationException> {
            config.signingPersistence()
        }
    }

    @Test
    fun `Should throw CryptoConfigurationException when HSM persistence is missing`() {
        val config = configFactory.create(ConfigFactory.empty())
        assertThrows<CryptoConfigurationException> {
            config.hsmPersistence()
        }
    }

    @Test
    fun `CryptoSigningPersistenceConfig should throw CryptoConfigurationException when expireAfterAccessMins is empty`() {
        val config = CryptoSigningPersistenceConfig(
            configFactory.create(ConfigFactory.empty())
        )
        assertThrows<CryptoConfigurationException> {
            config.keysExpireAfterAccessMins
        }
    }

    @Test
    fun `CryptoSigningPersistenceConfig should throw CryptoConfigurationException when maximumSize is empty`() {
        val config = CryptoSigningPersistenceConfig(
            configFactory.create(ConfigFactory.empty())
        )
        assertThrows<CryptoConfigurationException> {
            config.keyNumberLimit
        }
    }

    @Test
    fun `CryptoSoftPersistenceConfig should throw CryptoConfigurationException when expireAfterAccessMins is empty`() {
        val config = CryptoSoftPersistenceConfig(
            configFactory.create(ConfigFactory.empty())
        )
        assertThrows<CryptoConfigurationException> {
            config.expireAfterAccessMins
        }
    }

    @Test
    fun `CryptoSoftPersistenceConfig should throw CryptoConfigurationException when maximumSize is empty`() {
        val config = CryptoSoftPersistenceConfig(
            configFactory.create(ConfigFactory.empty())
        )
        assertThrows<CryptoConfigurationException> {
            config.maximumSize
        }
    }

    @Test
    fun `CryptoSoftPersistenceConfig should throw CryptoConfigurationException when salt is empty`() {
        val config = CryptoSoftPersistenceConfig(
            configFactory.create(ConfigFactory.empty())
        )
        assertThrows<CryptoConfigurationException> {
            config.salt
        }
    }

    @Test
    fun `CryptoSoftPersistenceConfig should throw CryptoConfigurationException when passphrase is empty`() {
        val config = CryptoSoftPersistenceConfig(
            configFactory.create(ConfigFactory.empty())
        )
        assertThrows<CryptoConfigurationException> {
            config.passphrase
        }
    }

    @Test
    fun `CryptoSoftPersistenceConfig should throw CryptoConfigurationException when retries is empty`() {
        val config = CryptoSoftPersistenceConfig(
            configFactory.create(ConfigFactory.empty())
        )
        assertThrows<CryptoConfigurationException> {
            config.maxAttempts
        }
    }

    @Test
    fun `CryptoSoftPersistenceConfig should throw CryptoConfigurationException when timeoutMills is empty`() {
        val config = CryptoSoftPersistenceConfig(
            configFactory.create(ConfigFactory.empty())
        )
        assertThrows<CryptoConfigurationException> {
            config.attemptTimeoutMills
        }
    }

    @Test
    fun `CryptoHSMPersistenceConfig should throw CryptoConfigurationException when expireAfterAccessMins is empty`() {
        val config = CryptoHSMPersistenceConfig(
            configFactory.create(ConfigFactory.empty())
        )
        assertThrows<CryptoConfigurationException> {
            config.expireAfterAccessMins
        }
    }

    @Test
    fun `CryptoHSMPersistenceConfig should throw CryptoConfigurationException when maximumSize is empty`() {
        val config = CryptoHSMPersistenceConfig(
            configFactory.create(ConfigFactory.empty())
        )
        assertThrows<CryptoConfigurationException> {
            config.maximumSize
        }
    }

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
            fallbackCryptoRootKey = KeyCredentials("root-passphrase", "root-salt"),
            fallbackSoftKey = KeyCredentials("soft-passphrase", "soft-salt")
        )
        val cryptoConfig = config.getConfig(BOOT_CRYPTO)
        val encryptorFromConfig = cryptoConfig.rootEncryptor()
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
        assertEquals(0, sofPersistence.maxAttempts)
        assertEquals(20000, sofPersistence.attemptTimeoutMills)
        val signingPersistence = cryptoConfig.signingPersistence()
        assertEquals(90, signingPersistence.keysExpireAfterAccessMins)
        assertEquals(20, signingPersistence.keyNumberLimit)
        assertEquals(120, signingPersistence.vnodesExpireAfterAccessMins)
        assertEquals(100, signingPersistence.vnodeNumberLimit)
        assertEquals(15, signingPersistence.connectionsExpireAfterAccessMins)
        assertEquals(2, signingPersistence.connectionNumberLimit)
        val hsmPersistence = cryptoConfig.hsmPersistence()
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
            fallbackSoftKey = KeyCredentials("soft-passphrase", "soft-salt")
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
        assertEquals(0, sofPersistence.maxAttempts)
        assertEquals(20000, sofPersistence.attemptTimeoutMills)
        val signingPersistence = cryptoConfig.signingPersistence()
        assertEquals(90, signingPersistence.keysExpireAfterAccessMins)
        assertEquals(20, signingPersistence.keyNumberLimit)
        assertEquals(120, signingPersistence.vnodesExpireAfterAccessMins)
        assertEquals(100, signingPersistence.vnodeNumberLimit)
        assertEquals(15, signingPersistence.connectionsExpireAfterAccessMins)
        assertEquals(2, signingPersistence.connectionNumberLimit)
        val hsmPersistence = cryptoConfig.hsmPersistence()
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
            fallbackSoftKey = KeyCredentials("soft-passphrase", "soft-salt")
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
        assertEquals(0, sofPersistence.maxAttempts)
        assertEquals(20000, sofPersistence.attemptTimeoutMills)
        val signingPersistence = cryptoConfig.signingPersistence()
        assertEquals(90, signingPersistence.keysExpireAfterAccessMins)
        assertEquals(20, signingPersistence.keyNumberLimit)
        assertEquals(120, signingPersistence.vnodesExpireAfterAccessMins)
        assertEquals(100, signingPersistence.vnodeNumberLimit)
        assertEquals(15, signingPersistence.connectionsExpireAfterAccessMins)
        assertEquals(2, signingPersistence.connectionNumberLimit)
        val hsmPersistence = cryptoConfig.hsmPersistence()
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
            fallbackSoftKey = KeyCredentials("soft-passphrase", "soft-salt")
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
        assertEquals(0, sofPersistence.maxAttempts)
        assertEquals(20000, sofPersistence.attemptTimeoutMills)
        val signingPersistence = cryptoConfig.signingPersistence()
        assertEquals(90, signingPersistence.keysExpireAfterAccessMins)
        assertEquals(20, signingPersistence.keyNumberLimit)
        assertEquals(120, signingPersistence.vnodesExpireAfterAccessMins)
        assertEquals(100, signingPersistence.vnodeNumberLimit)
        assertEquals(15, signingPersistence.connectionsExpireAfterAccessMins)
        assertEquals(2, signingPersistence.connectionNumberLimit)
        val hsmPersistence = cryptoConfig.hsmPersistence()
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
                        "hsmPersistence.downstreamRetries" to 17
                    )
                )
            )
        ).addDefaultBootCryptoConfig(
            fallbackCryptoRootKey = KeyCredentials("root-passphrase", "root-salt"),
            fallbackSoftKey = KeyCredentials("soft-passphrase", "soft-salt")
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
        assertEquals(0, sofPersistence.maxAttempts)
        assertEquals(20000, sofPersistence.attemptTimeoutMills)
        val signingPersistence = cryptoConfig.signingPersistence()
        assertEquals(42, signingPersistence.keysExpireAfterAccessMins)
        assertEquals(21, signingPersistence.keyNumberLimit)
        assertEquals(127, signingPersistence.vnodesExpireAfterAccessMins)
        assertEquals(55, signingPersistence.vnodeNumberLimit)
        assertEquals(17, signingPersistence.connectionsExpireAfterAccessMins)
        assertEquals(3, signingPersistence.connectionNumberLimit)
        val hsmPersistence = cryptoConfig.hsmPersistence()
        assertEquals(11, hsmPersistence.expireAfterAccessMins)
        assertEquals(222, hsmPersistence.maximumSize)
        assertEquals(17, hsmPersistence.downstreamMaxAttempts)
        assertTrue(config.hasPath("instanceId"))
    }
}