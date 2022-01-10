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
    fun startRPCSender(config: SmartConfig)

    /** Sets the timeout for incoming HTTP RPC requests to [millis]. */
    fun setTimeout(millis: Int)

    /**
     * Updates cluster configuration.
     *
     * @throws `ConfigRPCOpsServiceException` If the updated configuration could not be published.
     * @throws `HttpApiException` If the request returns an exceptional response.
     */
    @HttpRpcPOST(description = "Update cluster configuration", path = "updateConfig")
    fun updateConfig(
        @HttpRpcRequestBodyParameter(description = "Details of the updated configuration", required = true)
        request: HTTPUpdateConfigRequest
    ): HTTPUpdateConfigResponse
}