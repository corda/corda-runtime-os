package net.corda.libs.configuration

import com.typesafe.config.Config
import net.corda.libs.configuration.secret.EncryptionSecretsServiceFactory
import net.corda.libs.configuration.secret.MaskedSecretsLookupService
import net.corda.libs.configuration.secret.SecretsConfigurationException
import net.corda.libs.configuration.secret.SecretsCreateService
import net.corda.libs.configuration.secret.SecretsServiceFactory
import net.corda.schema.configuration.BootConfig
import net.corda.schema.configuration.ConfigKeys
import org.slf4j.LoggerFactory

interface SmartConfigFactory {
    companion object {
        const val SECRET_SERVICE_TYPE = "${BootConfig.BOOT_SECRETS}.${ConfigKeys.SECRETS_TYPE}"
        private val logger = LoggerFactory.getLogger(this::class.java.enclosingClass)

        /**
         * Create a [SmartConfigFactory] that is initialized with the [SecretsService] that is relevant based on the
         * [secretsServiceConfig] that is passed in.
         *
         * @param secretsServiceConfig Typesafe config object that defines which [secretsService] should be used.
         * @param secretsServiceFactories Available implementations of [SecretsServiceFactory]
         */
        fun createWith(secretsServiceConfig: Config, secretsServiceFactories: Collection<SecretsServiceFactory>): SmartConfigFactory {
            // select type from the config, and fall back on the EncryptionSecretsServiceFactory
            val type = secretsServiceConfig.getStringOrDefault(SECRET_SERVICE_TYPE, EncryptionSecretsServiceFactory.TYPE)

            secretsServiceFactories.filter { it.type == type }.let { factoriesOfMatchingType ->
                if (factoriesOfMatchingType.isEmpty()) {
                    throw SecretsConfigurationException("SecretsServiceFactory of type $type is not available.")
                } else if (factoriesOfMatchingType.size > 1) {
                    logger.warn("Multiple SecretsServiceFactory of type $type are available, will select first.")
                }
                return SmartConfigFactoryImpl(factoriesOfMatchingType.first().create(secretsServiceConfig))
            }
        }

        /**
         * Create a [SmartConfigFactory] that doesn't support secrets.
         *
         */
        fun createWithoutSecurityServices() = SmartConfigFactoryImpl(
            MaskedSecretsLookupService(),
            object : SecretsCreateService {
                override fun createValue(plainText: String, key: String): Config {
                    throw SecretsConfigurationException("This SmartConfigFactory does not support creating secrets.")
                }
            }
        )
    }

    /**
     * Convert a regular [Config] object into a [SmartConfig] one that is able to resolve secrets
     * using the given implementation of [SecretsLookupService].
     *
     * @param config
     * @return
     */
    fun create(config: Config): SmartConfig

    fun makeSecret(plainText: String, key: String): SmartConfig

    /*
     * Create an empty smart configuration object
     */
    fun empty(): SmartConfig
}
