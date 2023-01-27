package net.corda.libs.configuration.secret

import com.typesafe.config.Config
import net.corda.schema.configuration.ConfigKeys
import net.corda.v5.base.util.debug
import org.osgi.service.component.annotations.Component
import org.slf4j.LoggerFactory

@Component(service = [SecretsServiceFactory::class])
class EncryptionSecretsServiceFactory : SecretsServiceFactory {
    companion object {
        const val TYPE = "encryption"
        const val SECRET_PASSPHRASE_KEY = "${ConfigKeys.SECRETS_CONFIG}.${ConfigKeys.SECRETS_PASSPHRASE}"
        const val SECRET_SALT_KEY = "${ConfigKeys.SECRETS_CONFIG}.${ConfigKeys.SECRETS_SALT}"

        private val logger = LoggerFactory.getLogger(this::class.java.enclosingClass)
    }

    override val type = TYPE

    override fun create(secretsServiceConfig: Config): EncryptionSecretsService {
        if (!secretsServiceConfig.hasPath(SECRET_PASSPHRASE_KEY) || !secretsServiceConfig.hasPath(SECRET_SALT_KEY)) {
            logger.debug { "Configuration not suitable for EncryptionSecretsService: ${secretsServiceConfig.root().render()}}" }
            throw SecretsConfigurationException("Could not create EncryptionSecretsService with the given configuration. " +
                    "Ensure `$SECRET_PASSPHRASE_KEY` and `$SECRET_SALT_KEY` has been provided.")
        }

        val passphrase = secretsServiceConfig.getString(SECRET_PASSPHRASE_KEY)
        val salt = secretsServiceConfig.getString(SECRET_SALT_KEY)
        if (passphrase.isBlank() || salt.isBlank())
            throw SecretsConfigurationException("Passphrase and Salt must not be blank or empty.")
        return EncryptionSecretsServiceImpl(
            passphrase,
            salt,
        )
    }
}