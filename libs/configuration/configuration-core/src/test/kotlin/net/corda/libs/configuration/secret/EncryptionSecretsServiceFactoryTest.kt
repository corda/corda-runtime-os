package net.corda.libs.configuration.secret

import com.typesafe.config.ConfigFactory
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

class EncryptionSecretsServiceFactoryTest {

    private val secretsConfig = mapOf(
        EncryptionSecretsServiceFactory.SECRET_SALT_KEY to "salt",
        EncryptionSecretsServiceFactory.SECRET_PASSPHRASE_KEY to "pass"
    )

    @Test
    fun `when salt and password provided, return service`() {
        val encryptionSecretsServiceFactory = EncryptionSecretsServiceFactory().create(ConfigFactory.parseMap(secretsConfig))
        assertThat(encryptionSecretsServiceFactory).isNotNull
    }

    @Test
    fun `when create with no secrets config, return null`() {
        val encryptionSecretsServiceFactory = EncryptionSecretsServiceFactory().create(ConfigFactory.empty())
        assertThat(encryptionSecretsServiceFactory).isNull()
    }

    @ParameterizedTest
    @ValueSource(strings = ["", " "])
    fun `when create and salt is blank throw`(salt: String) {
        assertThrows<SecretsConfigurationException> {
            EncryptionSecretsServiceFactory().create(ConfigFactory.parseMap(mapOf(
                EncryptionSecretsServiceFactory.SECRET_SALT_KEY to salt,
                EncryptionSecretsServiceFactory.SECRET_PASSPHRASE_KEY to "pass"
            )))
        }
    }

    @ParameterizedTest
    @ValueSource(strings = ["", " "])
    fun `when create and passphrase is blank throw`(passphrase: String) {
        assertThrows<SecretsConfigurationException> {
            EncryptionSecretsServiceFactory().create(ConfigFactory.parseMap(mapOf(
                EncryptionSecretsServiceFactory.SECRET_SALT_KEY to "salt",
                EncryptionSecretsServiceFactory.SECRET_PASSPHRASE_KEY to passphrase
            )))
        }
    }
}