package net.corda.libs.configuration

import com.typesafe.config.Config
import net.corda.libs.configuration.secret.EncryptionSecretsServiceFactory
import net.corda.libs.configuration.secret.MaskedSecretsLookupService
import net.corda.libs.configuration.secret.SecretsConfigurationException
import net.corda.libs.configuration.secret.SecretsCreateService
import net.corda.libs.configuration.secret.SecretsServiceFactory
import net.corda.libs.configuration.secret.SecretsServiceFactoryResolver
import net.corda.schema.configuration.ConfigKeys
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference

/**
 * Factory for creating [SmartConfigFactory] objects based on configuration.
 * This means that implementations of the [SecretsServiceFactory] can be discovered at
 * start-up and resolved at runtime, allowing us to decouple the implementations from their use.
 *
 * @property secretsServiceFactoryResolver to be able to fetch available implementations of [SecretsServiceFactory].
 */
@Component(service = [SmartConfigFactoryFactory::class])
class SmartConfigFactoryFactory
    @Activate
    constructor(
        // discover all "SecretsServiceFactory" services available through OSGi injection.
        @Reference(service = SecretsServiceFactoryResolver::class)
        private val secretsServiceFactoryResolver: SecretsServiceFactoryResolver
    ) {

    companion object {
        const val SECRET_SERVICE_TYPE = "${ConfigKeys.SECRETS_CONFIG}.${ConfigKeys.SECRETS_TYPE}"

        /**
         * Create a [SmartConfigFactory] that doesn't support secrets.
         *
         */
        fun createWithoutSecurityServices() = SmartConfigFactoryImpl(
            MaskedSecretsLookupService(),
            object : SecretsCreateService {
                override fun createValue(plainText: String): Config {
                    throw SecretsConfigurationException("This SmartConfigFactory does not support creating secrets.")
                }
            })
    }

    /**
     * Create a [SmartConfigFactory] that is initialized with the [SecretsService] that is relevant based on the
     * [secretsServiceConfig] that is passed in.
     *
     * Falls back on the [MaskedSecretsLookupService] in case no relevant [SecretsServiceFactory] can be found.
     *
     * @param secretsServiceConfig Typesafe config object that defines which [secretsService] should be used.
     */
    fun create(secretsServiceConfig: Config): SmartConfigFactory {
        // select type from the config, and fall back on the EncryptionSecretsServiceFactory
        val type = secretsServiceConfig.getStringOrDefault(SECRET_SERVICE_TYPE, EncryptionSecretsServiceFactory.TYPE)

        val secretsServiceFactory = secretsServiceFactoryResolver
            .findAll()
            .singleOrNull { it.type == type }
                ?: throw SecretsConfigurationException("SecretsServiceFactory of type $type is not available.")

        return SmartConfigFactoryImpl(secretsServiceFactory.create(secretsServiceConfig))
    }
}

