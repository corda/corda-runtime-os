package net.corda.libs.configuration.secret

import com.typesafe.config.ConfigFactory
import com.typesafe.config.Config
import net.corda.libs.configuration.SmartConfig

/**
 * Secrets service using simple encryption.
 *
 * @constructor Create new [EncryptionSecretsServiceImpl]
 * @param passphrase for deriving encryption key
 * @param salt to for deriving encryption key
 * @param encryptor
 * @param decryptor
 */
class EncryptionSecretsServiceImpl(
    private val passphrase: String,
    private val salt: String,
    private val encryptor: SecretEncryptor = SecretEncryptionUtil(),
    private val decryptor: SecretDecryptor = SecretEncryptionUtil(),
): EncryptionSecretsService {
    override fun getValue(secretConfig: Config): String {
        if(!secretConfig.hasPath(SmartConfig.SECRET_KEY))
            throw SecretsConfigurationException("secretConfig is not a valid Secret Config Section: $secretConfig")
        if(!secretConfig.hasPath("${SmartConfig.SECRET_KEY}.${EncryptionSecretsService.SECRET_KEY}"))
            throw SecretsConfigurationException("You are using the ${this::class.java.name} secrets service, " +
                    "but your secret configuration does not have the expected ${EncryptionSecretsService.SECRET_KEY}. " +
                    "Config: $secretConfig")
        val secretValue = secretConfig.getString("${SmartConfig.SECRET_KEY}.${EncryptionSecretsService.SECRET_KEY}")
        if(secretValue.isBlank())
            throw SecretsConfigurationException("The value for ${EncryptionSecretsService.SECRET_KEY} must be a non-blank string")
        return decryptor.decrypt(secretValue, salt, passphrase)
    }

    override fun createValue(plainText: String, path: String): Config {
        val encryptedSecret = encryptor.encrypt(plainText, salt, passphrase)
        val secretConfig = mapOf(
            "${SmartConfig.SECRET_KEY}.${EncryptionSecretsService.SECRET_KEY}" to encryptedSecret,
        )
        return ConfigFactory.parseMap(secretConfig)
    }
}

