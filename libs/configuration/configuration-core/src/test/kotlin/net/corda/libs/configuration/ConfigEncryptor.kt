package net.corda.libs.configuration

import com.typesafe.config.ConfigFactory
import net.corda.libs.configuration.secret.EncryptionSecretsServiceFactory
import net.corda.libs.configuration.secret.EncryptionSecretsServiceImpl
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class ConfigEncryptor {
    /** NOTE: this isn't really a test, but an easy way to generate encrypted configuration.
     * This should be removed once we have this utility in corda-cli
     */
    @Test
    fun createConfigSection() {
        val salt = "not_so_random"
        val passphrase = "bad_passphrase"
        val passwordToEncrypt = "pass"

        val encryptionService = EncryptionSecretsServiceImpl(passphrase, salt)

        val configSection = encryptionService.createValue(passwordToEncrypt< '')

        val secretsConfig = mapOf(
            EncryptionSecretsServiceFactory.SECRET_SALT_KEY to salt,
            EncryptionSecretsServiceFactory.SECRET_PASSPHRASE_KEY to passphrase
        )

        val configFactory = SmartConfigFactory.createWith(
            ConfigFactory.parseMap(secretsConfig),
            listOf(EncryptionSecretsServiceFactory())
        )
        val config = configFactory.create(configSection)

        println("Config section:")
        println(config.root().render())

        // check we can decrypt again as part of a sample config.
        val exampleConfig = configFactory.create(config.atKey("example"))
        assertThat(exampleConfig.getString("example")).isEqualTo(passwordToEncrypt)
    }
}