package net.corda.sdk.config

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ObjectNode
import net.corda.libs.configuration.endpoints.v1.ConfigRestResource
import net.corda.libs.configuration.endpoints.v1.types.ConfigSchemaVersion
import net.corda.libs.configuration.endpoints.v1.types.GetConfigResponse
import net.corda.libs.configuration.endpoints.v1.types.UpdateConfigParameters
import net.corda.rest.client.RestClient
import net.corda.rest.json.serialization.JsonObjectAsString
import net.corda.sdk.rest.RestClientUtils.executeWithRetry
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

class ClusterConfig {

    private val objectMapper = ObjectMapper()

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

    fun configureCrl(
        restClient: RestClient<ConfigRestResource>,
        mode: String,
        currentConfig: GetConfigResponse,
        wait: Duration = 10.seconds
    ) {
        val newConfig = objectMapper.createObjectNode()
        newConfig.set<ObjectNode>(
            "sslConfig",
            objectMapper.createObjectNode()
                .set<ObjectNode>(
                    "revocationCheck",
                    objectMapper.createObjectNode().put("mode", mode.uppercase()),
                ),
        )

        val payload = UpdateConfigParameters(
            section = "corda.p2p.gateway",
            version = currentConfig.version,
            config = JsonObjectAsString(objectMapper.writeValueAsString(newConfig)),
            schemaVersion = ConfigSchemaVersion(major = currentConfig.schemaVersion.major, minor = currentConfig.schemaVersion.minor),
        )

        updateConfig(restClient, payload, wait)
    }

    fun configureTlsType(
        restClient: RestClient<ConfigRestResource>,
        tlsType: String,
        currentConfig: GetConfigResponse,
        wait: Duration = 10.seconds
    ) {
        val newConfig = objectMapper.createObjectNode()
        newConfig.set<ObjectNode>(
            "sslConfig",
            objectMapper.createObjectNode()
                .put("tlsType", tlsType.uppercase())
        )

        val payload = UpdateConfigParameters(
            section = "corda.p2p.gateway",
            version = currentConfig.version,
            config = JsonObjectAsString(objectMapper.writeValueAsString(newConfig)),
            schemaVersion = ConfigSchemaVersion(major = currentConfig.schemaVersion.major, minor = currentConfig.schemaVersion.minor),
        )
        updateConfig(restClient, payload, wait)
    }

    fun updateConfig(restClient: RestClient<ConfigRestResource>, updateConfig: UpdateConfigParameters, wait: Duration = 10.seconds) {
        restClient.use { client ->
            executeWithRetry(
                waitDuration = wait,
                operationName = "Update cluster config"
            ) {
                val resource = client.start().proxy
                resource.updateConfig(updateConfig)
            }
        }
    }
}
