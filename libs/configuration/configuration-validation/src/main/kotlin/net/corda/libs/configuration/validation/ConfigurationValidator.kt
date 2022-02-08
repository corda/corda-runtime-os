package net.corda.libs.configuration.validation

import net.corda.libs.configuration.SmartConfig

/**
 * Validator of configuration data against a configuration schema.
 *
 * Validation takes place on a per top-level configuration key basis.
 */
interface ConfigurationValidator {

    /**
     * Validate some configuration data for a provided top-level key.
     *
     * Validation will take place against a particular version of the configuration schema, which must be provided when
     * calling this function. The validator will attempt to retrieve the schema, and if it succeeds will then validate
     * against that schema.
     *
     * If this function returns, then validation was successful. A [ConfigurationValidationException] is thrown if the
     * configuration fails to validate, which contains information about what errors were encountered with the data.
     *
     * @param key The top-level configuration key this data represents
     * @param version The schema version this data should be validated against
     * @param config The configuration data to validate
     *
     * @throws ConfigurationValidationException If the configuration fails to validate
     * @throws ConfigurationSchemaFetchException If some or all of the requested schema does not exist. This is most
     *                                           likely to happen due to an incorrect version being provided
     */
    fun validate(key: String, version: String, config: SmartConfig)
}