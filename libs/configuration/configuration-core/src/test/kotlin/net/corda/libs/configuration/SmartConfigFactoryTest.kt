package net.corda.libs.configuration

import com.typesafe.config.ConfigFactory
import net.corda.crypto.core.Encryptor
import net.corda.libs.configuration.secret.EncryptionSecretsService
import net.corda.libs.configuration.secret.MaskedSecretsLookupService
import net.corda.libs.configuration.secret.SecretsConfigurationException
import net.corda.v5.base.util.toBase64
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

class SmartConfigFactoryTest {

    private val secretsConfig = mapOf(
        SmartConfigFactory.SECRET_SALT_KEY to "salt",
        SmartConfigFactory.SECRET_PASSPHRASE_KEY to "pass"
    )

    private val encryptor = Encryptor.derive("pass", "salt")
    private val secretValue = encryptor.encrypt("hello world".toByteArray()).toBase64()

    private val configWithSecret = """
        {
            "foo": {
                "${SmartConfig.SECRET_KEY}": {
                    "${EncryptionSecretsService.SECRET_KEY}": "$secretValue"
                }
            }
        }
    """.trimIndent()

    @Test
    fun `when create with default config create encryption secrets service`() {
        val smartConfigFactory = SmartConfigFactory.create(ConfigFactory.parseMap(secretsConfig))
        val encryptedConfig = smartConfigFactory.create(ConfigFactory.parseString(configWithSecret))

        assertThat(encryptedConfig.getString("foo")).isEqualTo("hello world")
    }

    @Test
    fun `when create with no secrets config create secrets service with no lookup service`() {
        val smartConfigFactory = SmartConfigFactory.create(ConfigFactory.empty())
        val encryptedConfig = smartConfigFactory.create(ConfigFactory.parseString(configWithSecret))

        assertThat(encryptedConfig.getString("foo")).isEqualTo(MaskedSecretsLookupService.MASK_VALUE)
    }

    @ParameterizedTest
    @ValueSource(strings = ["", " "])
    fun `when create and salt is blank throw`(salt: String) {
        assertThrows<SecretsConfigurationException> {
            SmartConfigFactory.create(ConfigFactory.parseMap(mapOf(
                SmartConfigFactory.SECRET_SALT_KEY to salt,
                SmartConfigFactory.SECRET_PASSPHRASE_KEY to "pass"
            )))
        }
    }

    @ParameterizedTest
    @ValueSource(strings = ["", " "])
    fun `when create and passphrase is blank throw`(passphrase: String) {
        assertThrows<SecretsConfigurationException> {
            SmartConfigFactory.create(ConfigFactory.parseMap(mapOf(
                SmartConfigFactory.SECRET_SALT_KEY to "salt",
                SmartConfigFactory.SECRET_PASSPHRASE_KEY to passphrase
            )))
        }
    }
}