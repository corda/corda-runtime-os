package net.corda.libs.configuration.secret

import com.typesafe.config.Config
import net.corda.schema.configuration.ConfigKeys
import net.corda.v5.base.util.contextLogger
import net.corda.v5.base.util.debug
import org.osgi.service.component.annotations.Component

@Component(service = [SecretsServiceFactory::class])
class EncryptionSecretsServiceFactory : SecretsServiceFactory {
    companion object {
        const val TYPE = "encryption"
        const val SECRET_PASSPHRASE_KEY = "${ConfigKeys.SECRETS_CONFIG}.${ConfigKeys.SECRETS_PASSPHRASE}"
        const val SECRET_SALT_KEY = "${ConfigKeys.SECRETS_CONFIG}.${ConfigKeys.SECRETS_SALT}"

        private val logger = contextLogger()
    }

    override val type = TYPE

    override fun create(config: Config): EncryptionSecretsService {
        if (!config.hasPath(SECRET_PASSPHRASE_KEY) || !config.hasPath(SECRET_SALT_KEY)) {
            logger.debug { "Configuration not suitable for EncryptionSecretsService: ${config.root().render()}}" }
            throw SecretsConfigurationException("Could not create EncryptionSecretsService with the given configuration. " +
                    "Ensure `$SECRET_PASSPHRASE_KEY` and `$SECRET_SALT_KEY` has been provided.")
        }

        val passphrase = config.getString(SECRET_PASSPHRASE_KEY)
        val salt = config.getString(SECRET_SALT_KEY)
        if (passphrase.isBlank() || salt.isBlank())
            throw SecretsConfigurationException("Passphrase and Salt must not be blank or empty.")
        return EncryptionSecretsServiceImpl(
            passphrase,
            salt,
        )
    }
}