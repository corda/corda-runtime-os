package net.corda.libs.configuration.endpoints.v1

import net.corda.httprpc.RpcOps
import net.corda.httprpc.annotations.HttpRpcPOST
import net.corda.httprpc.annotations.HttpRpcRequestBodyParameter
import net.corda.httprpc.annotations.HttpRpcResource
import net.corda.libs.configuration.endpoints.v1.types.HTTPUpdateConfigRequest
import net.corda.libs.configuration.endpoints.v1.types.HTTPUpdateConfigResponse

/** RPC operations for cluster configuration management. */
@HttpRpcResource(
    name = "ConfigRPCOps",
    description = "Cluster configuration management endpoints",
    path = "config"
)
interface ConfigRPCOps : RpcOps {

    /**
     * Updates cluster configuration.
     *
     * @throws `ConfigRPCOpsServiceException` If the updated configuration could not be published.
     * @throws `HttpApiException` If the request returns an exceptional response.
     */
    @HttpRpcPOST(
        path = "update",
        title = "Update cluster configuration",
        description = "Updates a section of the cluster configuration.",
        responseDescription = "The updated cluster configuration for the specified section."
    )
    fun updateConfig(
        @HttpRpcRequestBodyParameter(description = "Details of the updated configuration")
        request: HTTPUpdateConfigRequest
    ): HTTPUpdateConfigResponse
}