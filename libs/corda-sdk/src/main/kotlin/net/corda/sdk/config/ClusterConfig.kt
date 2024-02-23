package net.corda.sdk.config

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ObjectNode
import net.corda.libs.configuration.endpoints.v1.ConfigRestResource
import net.corda.libs.configuration.endpoints.v1.types.ConfigSchemaVersion
import net.corda.libs.configuration.endpoints.v1.types.GetConfigResponse
import net.corda.libs.configuration.endpoints.v1.types.UpdateConfigParameters
import net.corda.rest.client.RestClient
import net.corda.rest.json.serialization.JsonObjectAsString
import net.corda.sdk.rest.InvariantUtils.MAX_ATTEMPTS
import net.corda.sdk.rest.InvariantUtils.checkInvariant

class ClusterConfig {

    private val objectMapper = ObjectMapper()

    fun getCurrentConfig(restClient: RestClient<ConfigRestResource>, configSection: String): GetConfigResponse {
        return restClient.use { client ->
            checkInvariant(
                errorMessage = "Failed to get cluster config after $MAX_ATTEMPTS attempts.",
            ) {
                try {
                    val resource = client.start().proxy
                    resource.get(configSection)
                } catch (e: Exception) {
                    null
                }
            }
        }
    }

    fun configureCrl(restClient: RestClient<ConfigRestResource>, mode: String, currentConfig: GetConfigResponse) {
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

        updateConfig(restClient, payload)
    }

    fun configureTlsType(restClient: RestClient<ConfigRestResource>, tlsType: String, currentConfig: GetConfigResponse) {
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
        updateConfig(restClient, payload)
    }

    fun updateConfig(restClient: RestClient<ConfigRestResource>, updateConfig: UpdateConfigParameters) {
        restClient.use { client ->
            checkInvariant(
                errorMessage = "Failed to update cluster config after $MAX_ATTEMPTS attempts.",
            ) {
                try {
                    val resource = client.start().proxy
                    resource.updateConfig(updateConfig)
                } catch (e: Exception) {
                    null
                }
            }
        }
    }
}
