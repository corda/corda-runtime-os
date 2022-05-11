package net.corda.libs.configuration.validation

/**
 * Factory for creating new configuration validators.
 */
interface ConfigurationValidatorFactory {

    /**
     * Create a new configuration validator.
     */
    fun createConfigValidator(): ConfigurationValidator
}