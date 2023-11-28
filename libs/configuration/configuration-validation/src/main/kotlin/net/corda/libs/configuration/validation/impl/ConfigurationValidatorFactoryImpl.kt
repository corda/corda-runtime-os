package net.corda.libs.configuration.validation.impl

import net.corda.libs.configuration.validation.ConfigurationValidator
import net.corda.libs.configuration.validation.ConfigurationValidatorFactory
import net.corda.schema.configuration.provider.SchemaProviderConfigFactory
import net.corda.schema.cordapp.configuration.provider.SchemaProviderCordappConfigFactory
import org.osgi.service.component.annotations.Component

/**
 * Factory for creating new configuration validators.
 */
@Component(service = [ConfigurationValidatorFactory::class])
class ConfigurationValidatorFactoryImpl : ConfigurationValidatorFactory {

    /**
     * Create a new configuration validator.
     */
    override fun createConfigValidator(): ConfigurationValidator {
        val schemaProvider = SchemaProviderConfigFactory.getSchemaProvider()
        return ConfigurationValidatorImpl(schemaProvider)
    }

    /**
     * Create a configuration validator for the external messaging
     */
    override fun createCordappConfigValidator(): ConfigurationValidator {
        val schemaProvider = SchemaProviderCordappConfigFactory.getSchemaProvider()
        return ConfigurationValidatorImpl(schemaProvider)
    }
}
