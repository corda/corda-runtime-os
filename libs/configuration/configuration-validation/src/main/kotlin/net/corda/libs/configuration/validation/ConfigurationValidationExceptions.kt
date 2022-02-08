package net.corda.libs.configuration.validation

import com.typesafe.config.ConfigRenderOptions
import net.corda.libs.configuration.SmartConfig

/**
 * Exception thrown when configuration validation fails.
 *
 * @param key The top-level configuration key that validation was requested for
 * @param schemaVersion The version of the schema that was requested for validation
 * @param config The configuration data that was validated
 * @param errors A set of error messages describing what went wrong when validating the config
 */
class ConfigurationValidationException(
    val key: String,
    val schemaVersion: String,
    val config: SmartConfig,
    val errors: Set<String>
) :
    RuntimeException(
        "Configuration failed to validate for key $key at schema version $schemaVersion: ${errors.joinToString(",")}. Full config: ${
            config.toSafeConfig().root().render(
                ConfigRenderOptions.concise()
            )
        }"
    )

/**
 * Exception thrown when the requested schema was not available.
 */
class ConfigurationSchemaFetchException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)