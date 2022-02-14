package net.corda.libs.configuration.validation

import com.typesafe.config.ConfigRenderOptions
import net.corda.libs.configuration.SmartConfig
import net.corda.v5.base.versioning.Version

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
    val schemaVersion: Version,
    val config: SmartConfig,
    val errors: Set<String>
) : RuntimeException(
    "Configuration failed to validate for key $key at schema version $schemaVersion: ${errors.joinToString(", ")}"
) {
    /**
     * Return full details about the validation error, including the full config that was validated.
     *
     * This should primarily be used for debug purposes, to avoid printing the full configuration (which may be large).
     */
    fun fullErrorDetail(): String {
        val renderOptions = ConfigRenderOptions.concise()
        renderOptions.formatted = true
        return """
            Configuration Validation Errors:
            Key: $key
            Schema Version: $schemaVersion
            Errors: ${errors.joinToString(", ")}
            Config: ${config.toSafeConfig().root().render(renderOptions)}
        """.trimIndent()
    }
}

/**
 * Exception thrown when the requested schema was not available.
 */
class ConfigurationSchemaFetchException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)