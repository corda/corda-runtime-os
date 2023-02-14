package net.corda.libs.configuration.secret

import com.typesafe.config.ConfigFactory
import net.corda.libs.configuration.SmartConfig
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify

class EncryptionSecretsServiceTest {
    val passphrase = "passphrase 1"
    val salt = "salt1"
    val configString = """
        root {
            "${SmartConfig.SECRET_KEY}": {
                "encryptedSecret":"encrypted_secret1",
            }
        }   
    """.trimIndent()

    val encryptorMock = mock<SecretEncryptor> {
        on { encrypt("secret1", "salt1", "passphrase 1") } doReturn "encrypted_secret1"
    }
    val decryptorMock = mock<SecretDecryptor> {
        on { decrypt("encrypted_secret1", "salt1", "passphrase 1") } doReturn "secret1"
    }

    @Test
    fun `when ConfigValue not secret section throw`() {
        val service = EncryptionSecretsServiceImpl(passphrase, salt, encryptorMock, decryptorMock)
        val config = ConfigFactory.parseString("""
        root {
            "encryptedSecret":"encrypted_secret1",
        }   
    """.trimIndent())

        assertThrows<SecretsConfigurationException> {
            service.getValue(config.getConfig("root"))
        }
    }

    @Test
    fun `when secret section empty throw`() {
        val service = EncryptionSecretsServiceImpl(passphrase, salt, encryptorMock, decryptorMock)
        val config = ConfigFactory.parseString("""
        root.${SmartConfig.SECRET_KEY} {
        }     
    """.trimIndent())

        assertThrows<SecretsConfigurationException> {
            service.getValue(config.getConfig("root"))
        }
    }

    @ParameterizedTest
    @ValueSource(strings = ["", " "])
    fun `when encryptedSecret empty or blank throw`(encryptedSecretValue: String) {
        val service = EncryptionSecretsServiceImpl(passphrase, salt, encryptorMock, decryptorMock)
        val config = ConfigFactory.parseString("""
        root.${SmartConfig.SECRET_KEY} {
            "encryptedSecret":"$encryptedSecretValue",
        }   
    """.trimIndent())

        assertThrows<SecretsConfigurationException> {
            service.getValue(config.getConfig("root"))
        }
    }

    @Test
    fun `when encryptedSecret missing throw`() {
        val service = EncryptionSecretsServiceImpl(passphrase, salt, encryptorMock, decryptorMock)
        val config = ConfigFactory.parseString("""
        root.${SmartConfig.SECRET_KEY} {
            "foo": "bar"
        }     
    """.trimIndent())

        assertThrows<SecretsConfigurationException> {
            service.getValue(config.getConfig("root"))
        }
    }

    @Test
    fun `when getValue decrypt using decryptor with correct passphrase and salt`() {
        val service = EncryptionSecretsServiceImpl(passphrase, salt, encryptorMock, decryptorMock)
        val config = ConfigFactory.parseString(configString)
        service.getValue(config.getConfig("root"))

        verify(decryptorMock).decrypt("encrypted_secret1", "salt1", "passphrase 1")
    }

    @Test
    fun `when createValue can decrypt again`() {
        val service = EncryptionSecretsServiceImpl(passphrase, salt, encryptorMock, decryptorMock)

        val secretConfig = service.createValue("secret1", "test")
        val secret = service.getValue(secretConfig)

        assertThat(secret).isEqualTo("secret1")
    }

    @Test
    fun `when createValue encrypt with encryptor with correct passphrase and salt`() {
        val service = EncryptionSecretsServiceImpl(passphrase, salt, encryptorMock, decryptorMock)
        val secretConfig = service.createValue("secret1", "test")

        secretConfig.atKey("root")

        verify(encryptorMock).encrypt("secret1", "salt1", "passphrase 1")
    }

    @Test
    fun `when createValue create correct paths`() {
        val service = EncryptionSecretsServiceImpl(passphrase, salt, encryptorMock, decryptorMock )

        val secretConfig1 = service.createValue("secret1", "test").atKey("root")

        assertThat(secretConfig1.hasPath("root.configSecret")).isEqualTo(true)
        assertThat(secretConfig1.getString("root.configSecret.encryptedSecret"))
            .isEqualTo("encrypted_secret1")
    }

    @Test
    fun `can encrypt and decrypt empty string`() {
        // using to real Encryptor for this test
        val service = EncryptionSecretsServiceImpl(passphrase, salt)

        val secretConfig = service.createValue("", "test")

        assertThat(secretConfig.getString("configSecret.encryptedSecret")).isNotBlank
        assertThat(service.getValue(secretConfig)).isEqualTo("")
    }
}