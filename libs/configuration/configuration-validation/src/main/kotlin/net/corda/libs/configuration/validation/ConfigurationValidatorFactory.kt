package net.corda.libs.configuration.validation

import net.corda.libs.configuration.validation.impl.ConfigurationValidatorImpl
import net.corda.schema.configuration.provider.SchemaProviderFactory

/**
 * Factory for creating new configuration validators.
 */
object ConfigurationValidatorFactory {

    /**
     * Create a new configuration validator.
     */
    fun getConfigValidator(): ConfigurationValidator {
        val schemaProvider = SchemaProviderFactory.getSchemaProvider()
        return ConfigurationValidatorImpl(schemaProvider)
    }
}