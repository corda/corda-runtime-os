package net.corda.libs.configuration.endpoints.v1

import net.corda.httprpc.RestResource
import net.corda.httprpc.annotations.HttpGET
import net.corda.httprpc.annotations.HttpPUT
import net.corda.httprpc.annotations.RestPathParameter
import net.corda.httprpc.annotations.RestRequestBodyParameter
import net.corda.httprpc.annotations.HttpRestResource
import net.corda.httprpc.exception.ResourceNotFoundException
import net.corda.httprpc.response.ResponseEntity
import net.corda.libs.configuration.endpoints.v1.types.GetConfigResponse
import net.corda.libs.configuration.endpoints.v1.types.UpdateConfigParameters
import net.corda.libs.configuration.endpoints.v1.types.UpdateConfigResponse

/** Rest operations for cluster configuration management. */
@HttpRestResource(
    name = "Configuration API",
    description = "The Configuration API consists of a number of endpoints used to manage the configuration of Corda clusters.",
    path = "config"
)
interface ConfigRestResource : RestResource {

    /**
     * Sends a request to update the clusters configuration.
     *
     * @param request Details of the updated configuration.
     * @throws ConfigRPCOpsServiceException If the updated configuration could not be published.
     * @throws HttpApiException If the request returns an exceptional response.
     */
    @HttpPUT(
        title = "Update cluster configuration",
        description = "This method updates a section of the cluster configuration.",
        responseDescription = """
            The updated cluster configuration for the specified section:
            - `section`: the section of the configuration to be updated.
            - `config`: the updated configuration in JSON or HOCON format.
            - `schemaVersion`: the schema version of the configuration.
            - `version`: the version number used for optimistic locking. The request fails if this version does not 
                match the version stored in the database for the corresponding section or -1 if this is a new section 
                for which no configuration has yet been stored."""
    )
    fun updateConfig(
        @RestRequestBodyParameter(description = """
            Details of the updated configuration. Includes:
            - `section`: the section of the configuration to be updated.
            - `config`: the updated configuration in JSON or HOCON format.
            - `schemaVersion`: the schema version of the configuration.
            - `version`: the version number used for optimistic locking. The request fails if this version does not 
                match the version stored in the database for the corresponding section or -1 if this is a new section 
                for which no configuration has yet been stored.""")
        request: UpdateConfigParameters
    ): ResponseEntity<UpdateConfigResponse>

    /**
     * Get the configuration data the cluster is set with for a specific [section].
     * @param section the top level section of cluster configuration to return.
     * @throws ResourceNotFoundException when the config [section] does not exist
     */
    @HttpGET(
        path = "{section}",
        title = "Get Configuration.",
        description = "This method returns the 'active' configuration for the given section, " +
                "in both the 'raw' format and with defaults applied.",
        responseDescription = "The configuration for the given section"
    )
    fun get(
        @RestPathParameter(description = "Section name for the configuration.")
        section: String
    ): GetConfigResponse
}