package net.corda.configuration.rest.impl.exception

import net.corda.data.config.ConfigurationSchemaVersion
import net.corda.rest.ResponseCode
import net.corda.rest.exception.HttpApiException

/**
 * Config version related exceptions.
 */
class ConfigException(
    errorType: String,
    errorMessage: String,
    schemaVersion: ConfigurationSchemaVersion,
    config: String
) : HttpApiException(
        responseCode = ResponseCode.INTERNAL_SERVER_ERROR,
        message = "$errorType: $errorMessage",
        details = mapOf(
            "schemaVersion" to "${schemaVersion.majorVersion}.${schemaVersion.minorVersion}",
            "config" to config
        )
    )

/**
 * Incorrect version for config update.
 */
class ConfigVersionConflictException(
    errorType: String,
    errorMessage: String,
    schemaVersion: ConfigurationSchemaVersion,
    config: String
) : HttpApiException(
    responseCode = ResponseCode.CONFLICT,
    message = "$errorType: $errorMessage",
    details = mapOf(
        "schemaVersion" to "${schemaVersion.majorVersion}.${schemaVersion.minorVersion}",
        "config" to config
    )
)