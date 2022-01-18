package net.corda.libs.configuration

import com.typesafe.config.Config
import net.corda.libs.configuration.secret.EncryptionSecretsServiceImpl
import net.corda.libs.configuration.secret.MaskedSecretsLookupService
import net.corda.libs.configuration.secret.SecretsConfigurationException
import net.corda.schema.configuration.ConfigKeys
import net.corda.v5.base.util.contextLogger

interface SmartConfigFactory {
    companion object {
        const val SECRET_PASSPHRASE_KEY = "${ConfigKeys.SECRETS_CONFIG}.${ConfigKeys.SECRETS_PASSPHRASE}"
        const val SECRET_SALT_KEY = "${ConfigKeys.SECRETS_CONFIG}.${ConfigKeys.SECRETS_SALT}"

        private val logger = contextLogger()

        /**
         * Create a [SmartConfigFactory] instance that is configured with a
         * [SecretsLookupService] depending on the config being passed in.
         *
         * @param config
         * @return
         */
        fun create(config: Config): SmartConfigFactory {

            if(config.hasPath(SECRET_PASSPHRASE_KEY) && config.hasPath(SECRET_SALT_KEY)) {
                val passphrase = config.getString(SECRET_PASSPHRASE_KEY)
                val salt = config.getString(SECRET_SALT_KEY)
                if(passphrase.isBlank() || salt.isBlank())
                    throw SecretsConfigurationException("Passphrase and Salt must not be blank or empty.")
                // assuming default (only for now) SecretsLookupService implementation
                return SmartConfigFactoryImpl(
                    EncryptionSecretsServiceImpl(
                    passphrase,
                    salt,
                )
                )
            }

            // if nothing configured, fall back to MaskedSecretsLookupService
            logger.warn("Secrets Provider not found from config: ${config.root().render()}")
            logger.warn("Falling back on MaskedSecretsLookupService. " +
                    "This means secrets configuration will not be supported and all configuration" +
                    "values that are marked as \"${SmartConfig.SECRET_KEY}\" will return " +
                    "\"${MaskedSecretsLookupService.MASK_VALUE}\" when resolved.")
            return SmartConfigFactoryImpl(MaskedSecretsLookupService())
        }
    }
    /**
     * Convert a regular [Config] object into a [SmartConfig] one that is able to resolve secrets
     * using the given implementation of [SecretsLookupService].
     *
     * @param config
     * @return
     */
    fun create(config: Config): SmartConfig
}