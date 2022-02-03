package net.corda.libs.configuration.validation

import net.corda.libs.configuration.validation.impl.ConfigurationValidatorImpl
import net.corda.schema.configuration.provider.SchemaProviderFactory

class ConfigurationValidatorFactory {

    fun getConfigValidator(): ConfigurationValidator {
        val schemaProvider = SchemaProviderFactory.getSchemaProvider()
        return ConfigurationValidatorImpl(schemaProvider)
    }
}