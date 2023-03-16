package net.corda.libs.configuration.validation

import com.typesafe.config.Config
import java.io.InputStream
import net.corda.libs.configuration.SmartConfig
import net.corda.v5.base.versioning.Version

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
     * @param applyDefaults Insert defaults into the config for fields which are not set. If a value is explicitly set to null in the
     * config defaults are not applied.
     *
     * @throws ConfigurationValidationException If the configuration fails to validate
     * @throws ConfigurationSchemaFetchException If some or all of the requested schema does not exist. This is most
     *                                           likely to happen due to an incorrect version being provided
     * @return The config returned as a SmartConfig. If [applyDefaults] is set to true, the returned config will have any empty fields set
     * to the defaults defined in the schema for this config [key]
     */
    fun validate(key: String, version: Version, config: SmartConfig, applyDefaults: Boolean = false) : SmartConfig

    /**
     * Validate some configuration data for a given schema
     *
     * If this function returns, then validation was successful. A [ConfigurationValidationException] is thrown if the
     * configuration fails to validate, which contains information about what errors were encountered with the data.
     *
     * @param key The configuration key being validated
     * @param config The configuration data to validate
     * @param schemaInput The schema file inputstream to validate against
     * @param applyDefaults Insert defaults into the config for fields which are not set. If a value is explicitly set to null in the
     * config defaults are not applied.
     *
     * @throws ConfigurationValidationException If the configuration fails to validate
     * @throws ConfigurationSchemaFetchException If some or all of the requested schema does not exist. This is most
     *                                           likely to happen due to an incorrect version being provided
     * @return The config returned as a SmartConfig. If [applyDefaults] is set to true, the returned config will have any empty fields set
     * to the defaults defined in the schema for this config [key]
     */
    fun validate(key: String, config: SmartConfig, schemaInput: InputStream, applyDefaults: Boolean = false) : SmartConfig

    /**
     * Retrieves default values from configuration schema.
     *
     * @param key The configuration key
     * @param version The schema version
     *
     * @throws ConfigurationValidationException If the configuration defaults retrieval fails
     * @throws ConfigurationSchemaFetchException If some or all of the requested schema does not exist. This is most
     *                                           likely to happen due to an incorrect version being provided
     * @return default values from configuration schema
     */
    fun getDefaults(key: String, version: Version) : Config
}