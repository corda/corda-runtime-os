package net.corda.crypto.impl

import com.typesafe.config.ConfigFactory
import com.typesafe.config.ConfigValueFactory
import net.corda.crypto.core.aes.AesEncryptor
import net.corda.crypto.core.aes.AesKey
import net.corda.crypto.core.aes.KeyCredentials
import net.corda.libs.configuration.SmartConfig
import net.corda.libs.configuration.SmartConfigFactory
import net.corda.schema.configuration.ConfigKeys.CRYPTO_CONFIG
import net.corda.schema.configuration.ConfigKeys.FLOW_CONFIG
import net.corda.v5.crypto.exceptions.CryptoConfigurationException
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.util.UUID
import kotlin.test.assertEquals

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
                cryptoRootKey = KeyCredentials("some-passphrase", "some-salt")
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
                passphrase = "some-passphrase",
                salt = "some-salt"
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
                .withValue("rootKey.salt", ConfigValueFactory.fromAnyRef("some-salt"))
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
                        configFactory.makeSecret("some-passphrase").root().unwrapped()
                    )
                )
        )
        assertThrows<CryptoConfigurationException> {
            config.rootEncryptor()
        }
    }

    @Test
    fun `Should be able to get soft persistence config and its properties`() {
        val config = smartConfig.softPersistence()
        assertEquals(240, config.expireAfterAccessMins)
        assertEquals(1000, config.maximumSize)
    }

    @Test
    fun `Should be able to get signing persistence config and its properties`() {
        val config = smartConfig.signingPersistence()
        assertEquals(90, config.expireAfterAccessMins)
        assertEquals(20, config.maximumSize)
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
    fun `CryptoPersistenceConfig should throw CryptoConfigurationException when expireAfterAccessMins is empty`() {
        val config = CryptoPersistenceConfig(
            configFactory.create(
                ConfigFactory.parseMap(
                    mapOf(
                        "maximumSize" to "26"
                    )
                )
            )
        )
        assertEquals(26, config.maximumSize)
        assertThrows<CryptoConfigurationException> {
            config.expireAfterAccessMins
        }
    }

    @Test
    fun `CryptoPersistenceConfig should throw CryptoConfigurationException when maximumSize is empty`() {
        val config = CryptoPersistenceConfig(
            configFactory.create(
                ConfigFactory.parseMap(
                    mapOf(
                        "expireAfterAccessMins" to "91",
                    )
                )
            )
        )
        assertEquals(91, config.expireAfterAccessMins)
        assertThrows<CryptoConfigurationException> {
            config.maximumSize
        }
    }
}