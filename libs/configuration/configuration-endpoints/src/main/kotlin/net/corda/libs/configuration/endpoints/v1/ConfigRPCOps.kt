package net.corda.libs.configuration.endpoints.v1

import net.corda.httprpc.RpcOps
import net.corda.httprpc.annotations.HttpRpcGET
import net.corda.httprpc.annotations.HttpRpcPUT
import net.corda.httprpc.annotations.HttpRpcPathParameter
import net.corda.httprpc.annotations.HttpRpcRequestBodyParameter
import net.corda.httprpc.annotations.HttpRpcResource
import net.corda.libs.configuration.endpoints.v1.types.GetConfigResponse
import net.corda.libs.configuration.endpoints.v1.types.UpdateConfigParameters
import net.corda.libs.configuration.endpoints.v1.types.UpdateConfigResponse

/** RPC operations for cluster configuration management. */
@HttpRpcResource(
    name = "Configuration API",
    description = "Cluster configuration management endpoints.",
    path = "config"
)
interface ConfigRPCOps : RpcOps {

    /**
     * Updates cluster configuration.
     *
     * @throws `ConfigRPCOpsServiceException` If the updated configuration could not be published.
     * @throws `HttpApiException` If the request returns an exceptional response.
     */
    @HttpRpcPUT(
        title = "Update cluster configuration",
        description = "Updates a section of the cluster configuration.",
        responseDescription = "The updated cluster configuration for the specified section."
    )
    fun updateConfig(
        @HttpRpcRequestBodyParameter(description = "Details of the updated configuration")
        request: UpdateConfigParameters
    ): UpdateConfigResponse

    @HttpRpcGET(
        path = "{section}",
        title = "Get Configuration.",
        description = "Get Configuration. Returns the 'active' configuration for the given section, " +
                "in both the 'raw' format and with defaults applied.",
        responseDescription = "The Configuration for the given section."
    )
    fun get(
        @HttpRpcPathParameter(description = "Section name for the configuration.")
        section: String
    ): GetConfigResponse
}