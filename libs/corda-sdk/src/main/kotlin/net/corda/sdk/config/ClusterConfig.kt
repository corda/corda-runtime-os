package net.corda.sdk.config

import net.corda.libs.configuration.endpoints.v1.ConfigRestResource
import net.corda.libs.configuration.endpoints.v1.types.GetConfigResponse
import net.corda.libs.configuration.endpoints.v1.types.UpdateConfigParameters
import net.corda.libs.configuration.endpoints.v1.types.UpdateConfigResponse
import net.corda.libs.configuration.exception.WrongConfigVersionException
import net.corda.rest.ResponseCode
import net.corda.rest.client.RestClient
import net.corda.sdk.rest.RestClientUtils.executeWithRetry
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

class ClusterConfig {

    /**
     * Get the current config from the Corda instance
     * @param restClient of type RestClient<ConfigRestResource>
     * @param configSection Section name for the configuration
     * @param wait Duration before timing out, default 10 seconds
     * @return a GetConfigResponse object
     */
    fun getCurrentConfig(
        restClient: RestClient<ConfigRestResource>,
        configSection: String,
        wait: Duration = 10.seconds
    ): GetConfigResponse {
        return restClient.use { client ->
            executeWithRetry(
                waitDuration = wait,
                operationName = "Get current config $configSection"
            ) {
                val resource = client.start().proxy
                resource.get(configSection)
            }
        }
    }

    /**
     * Update the section config from the Corda instance
     * @param restClient of type RestClient<ConfigRestResource>
     * @param updateConfig of type UpdateConfigParameters to submit
     * @param wait Duration before timing out, default 10 seconds
     * @return a UpdateConfigResponse if successful, or an exception
     */
    fun updateConfig(
        restClient: RestClient<ConfigRestResource>,
        updateConfig: UpdateConfigParameters,
        wait: Duration = 10.seconds
    ): UpdateConfigResponse {
        return restClient.use { client ->
            executeWithRetry(
                waitDuration = wait,
                operationName = "Update cluster config"
            ) {
                val resource = client.start().proxy
                val response = resource.updateConfig(updateConfig)
                if (response.responseCode == ResponseCode.ACCEPTED) {
                    response.responseBody
                } else if (response.responseCode == ResponseCode.CONFLICT) {
                    throw WrongConfigVersionException("Mismatch between config version: ${response.responseBody}")
                } else {
                    throw ConfigException("Error when updating config: ${response.responseBody}")
                }
            }
        }
    }
}

internal class ConfigException(message: String) : Exception(message)
