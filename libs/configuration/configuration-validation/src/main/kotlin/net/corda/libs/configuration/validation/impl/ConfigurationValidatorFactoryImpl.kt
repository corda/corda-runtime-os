package net.corda.libs.configuration.validation.impl

import net.corda.libs.configuration.validation.ConfigurationValidator
import net.corda.libs.configuration.validation.ConfigurationValidatorFactory
import net.corda.schema.common.provider.SchemaProviderFactory
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
        val schemaProvider = SchemaProviderFactory.getConfigSchemaProvider()
        return ConfigurationValidatorImpl(schemaProvider)
    }

    /**
     * Create a configuration validator for the external messaging
     */
    override fun createCordappConfigValidator(): ConfigurationValidator {
        val schemaProvider = SchemaProviderFactory.getCordappConfigSchemaProvider()
        return ConfigurationValidatorImpl(schemaProvider)
    }
}