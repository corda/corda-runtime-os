package net.corda.sdk.config

import net.corda.libs.configuration.endpoints.v1.types.GetConfigResponse
import net.corda.libs.configuration.endpoints.v1.types.UpdateConfigResponse
import net.corda.libs.configuration.exception.WrongConfigVersionException
import net.corda.rest.ResponseCode
import net.corda.restclient.CordaRestClient
import net.corda.restclient.dto.UpdateConfigParametersObjectNode
import net.corda.restclient.generated.infrastructure.ClientError
import net.corda.restclient.generated.infrastructure.Success
import net.corda.schema.configuration.ConfigKeys.RootConfigKey
import net.corda.sdk.rest.RestClientUtils.executeWithRetry
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

class ClusterConfig(val restClient: CordaRestClient) {

    /**
     * Get the current config from the Corda instance
     * @param configSection Section name for the configuration
     * @param wait Duration before timing out, default 10 seconds
     * @return a GetConfigResponse object
     */
    fun getCurrentConfig(
        configSection: RootConfigKey,
        wait: Duration = 10.seconds
    ): GetConfigResponse {
        return executeWithRetry(
            waitDuration = wait,
            operationName = "Get current config $configSection"
        ) {
            restClient.configurationClient.getConfigSection(configSection.value)
        }
    }

    /**
     * Update the section config from the Corda instance
     * @param updateConfig of type UpdateConfigParametersString to submit
     * @param wait Duration before timing out, default 10 seconds
     * @return a UpdateConfigResponse if successful, or an exception
     */
    fun updateConfig(
        updateConfig: UpdateConfigParametersObjectNode,
        wait: Duration = 10.seconds
    ): UpdateConfigResponse {
        return executeWithRetry(
            waitDuration = wait,
            operationName = "Update cluster config"
        ) {
            val response = restClient.configurationClient.putConfigWithHttpInfo(updateConfig)
            if (response.statusCode == ResponseCode.ACCEPTED.statusCode) {
                (response as Success<*>).data as UpdateConfigResponse
            } else {
                val localVarError = response as ClientError<*>
                if (response.statusCode == ResponseCode.CONFLICT.statusCode) {
                    throw WrongConfigVersionException(
                        "Mismatch between config version: ${localVarError.message} and the supplied: $updateConfig"
                    )
                } else {
                    throw ConfigException("Error when updating config: ${localVarError.message}")
                }
            }
        }
    }
}

internal class ConfigException(message: String) : Exception(message)
