package net.corda.libs.configuration.validation.impl

import net.corda.libs.configuration.validation.ConfigurationValidator
import net.corda.libs.configuration.validation.ConfigurationValidatorFactory
import net.corda.libs.configuration.validation.ExternalChannelsConfigValidator
import net.corda.schema.configuration.provider.SchemaProviderConfigFactory
import net.corda.schema.cordapp.configuration.provider.SchemaProviderCordappConfigFactory
import org.osgi.service.component.annotations.Component

/**
 * Factory for creating new configuration validators.
 */
@Component(service = [ConfigurationValidatorFactory::class])
class ConfigurationValidatorFactoryImpl : ConfigurationValidatorFactory {

    override fun createConfigValidator(): ConfigurationValidator {
        val schemaProvider = SchemaProviderConfigFactory.getSchemaProvider()
        return ConfigurationValidatorImpl(schemaProvider)
    }

    override fun createCordappConfigValidator(): ConfigurationValidator {
        val schemaProvider = SchemaProviderCordappConfigFactory.getSchemaProvider()
        return ConfigurationValidatorImpl(schemaProvider)
    }

    override fun createExternalChannelsConfigValidator(): ExternalChannelsConfigValidator {
        return ExternalChannelsConfigValidatorImpl(createCordappConfigValidator())
    }
}