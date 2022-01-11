package net.corda.configuration.rpcops.impl.v1

import net.corda.configuration.rpcops.impl.v1.types.HTTPUpdateConfigRequest
import net.corda.configuration.rpcops.impl.v1.types.HTTPUpdateConfigResponse
import net.corda.httprpc.RpcOps
import net.corda.httprpc.annotations.HttpRpcPOST
import net.corda.httprpc.annotations.HttpRpcRequestBodyParameter
import net.corda.httprpc.annotations.HttpRpcResource
import net.corda.libs.configuration.SmartConfig
import net.corda.lifecycle.Lifecycle

/** RPC operations for cluster configuration management. */
@HttpRpcResource(
    name = "ConfigRPCOps",
    description = "Cluster Configuration Management APIs",
    path = "config"
)
internal interface ConfigRPCOps : RpcOps, Lifecycle {
    /** Starts the RPC sender that handles incoming HTTP RPC requests using the given [config]. */
    fun createAndStartRPCSender(config: SmartConfig)

    /** Sets the timeout for incoming HTTP RPC requests to [millis]. */
    fun setTimeout(millis: Int)

    /**
     * Updates cluster configuration.
     *
     * @throws `ConfigRPCOpsServiceException` If the updated configuration could not be published.
     * @throws `HttpApiException` If the request returns an exceptional response.
     */
    @HttpRpcPOST(
        path = "config",
        title = "Update cluster configuration",
        description = "Updates a section of the cluster configuration.",
        responseDescription = "The updated cluster configuration for the specified section."
    )
    fun updateConfig(
        @HttpRpcRequestBodyParameter(description = "Details of the updated configuration", required = true)
        request: HTTPUpdateConfigRequest
    ): HTTPUpdateConfigResponse
}