package net.corda.libs.configuration.validation

/**
 * Factory for creating new configuration validators.
 */
interface ConfigurationValidatorFactory {

    /**
     * Create a new configuration validator.
     */
    fun createConfigValidator(): ConfigurationValidator

    /**
     * Create a configuration validator for a cordapp.
     */
    fun createCordappConfigValidator(): ConfigurationValidator

    /**
     * Create a configuration validator for the external messaging.
     */
    fun createExternalChannelsConfigValidator(): ExternalChannelsConfigValidator
}