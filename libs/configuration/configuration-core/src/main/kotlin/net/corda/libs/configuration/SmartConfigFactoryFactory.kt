package net.corda.libs.configuration

import com.typesafe.config.Config
import net.corda.libs.configuration.secret.MaskedSecretsLookupService
import net.corda.libs.configuration.secret.SecretsConfigurationException
import net.corda.libs.configuration.secret.SecretsCreateService
import net.corda.libs.configuration.secret.SecretsServiceFactory
import net.corda.v5.base.util.contextLogger
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import org.osgi.service.component.annotations.ReferenceCardinality

/**
 * Factory for creating [SmartConfigFactory] objects based on configuration.
 * This means that implementations of the [SecretsServiceFactory] can be discovered at
 * start-up and resolved at runtime, allowing us to decouple the implementations from their use.
 *
 * @property secretsServiceFactories list of all available implementations of [SecretsServiceFactory].
 */
@Component(service = [SmartConfigFactoryFactory::class])
class SmartConfigFactoryFactory
    @Activate
    constructor(
        // discover all "SecretsServiceFactory" services available through OSGi injection.
        @Reference(service = SecretsServiceFactory::class, cardinality = ReferenceCardinality.MULTIPLE)
        private val secretsServiceFactories: List<SecretsServiceFactory>
    ) {

    companion object {
        private val logger = contextLogger()

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
     * [config] that is passed in.
     *
     * Falls back on the [MaskedSecretsLookupService] in case no relevant [SecretsServiceFactory] can be found.
     *
     * @param config Typesafe config object that defines which [secretsService] should be used.
     */
    fun create(config: Config): SmartConfigFactory {
        secretsServiceFactories.forEach {
            val secretsServiceFactory = it.create(config)
            if(null !== secretsServiceFactory) return SmartConfigFactoryImpl(secretsServiceFactory)
        }

        // if none could be resolved, fall back to MaskedSecretsLookupService
        logger.info("Secrets Provider not found from config: ${config.root()?.render()}. " +
                "Available factories: ${secretsServiceFactories.map { it.javaClass.simpleName }}")
        logger.warn( "Falling back on MaskedSecretsLookupService. " +
                "This means secrets configuration will not be supported and all configuration" +
                "values that are marked as \"${SmartConfig.SECRET_KEY}\" will return " +
                "\"${MaskedSecretsLookupService.MASK_VALUE}\" when resolved.")


        return createWithoutSecurityServices()
    }
}