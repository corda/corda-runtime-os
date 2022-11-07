package net.corda.configuration.rpcops.impl.exception

import net.corda.data.config.ConfigurationSchemaVersion
import net.corda.httprpc.ResponseCode
import net.corda.httprpc.exception.HttpApiException

/**
 * Config version related exceptions.
 */
class ConfigVersionException(
    errorType: String,
    responseCode: ResponseCode,
    errorMessage: String, schemaVersion:
    ConfigurationSchemaVersion,
    config: String
) : HttpApiException(
        responseCode = responseCode,
        message = "$errorType: $errorMessage",
        details = mapOf(
            "schemaVersion" to "${schemaVersion.majorVersion}.${schemaVersion.minorVersion}",
            "config" to config
        )
    )