package net.corda.configuration.rpcops.impl.v1

import net.corda.configuration.rpcops.impl.v1.types.HTTPUpdateConfigRequest
import net.corda.configuration.rpcops.impl.v1.types.HTTPUpdateConfigResponse
import net.corda.httprpc.RpcOps
import net.corda.httprpc.annotations.HttpRpcPOST
import net.corda.httprpc.annotations.HttpRpcRequestBodyParameter
import net.corda.httprpc.annotations.HttpRpcResource
import net.corda.libs.configuration.SmartConfig
import java.io.Closeable

/** RPC operations for cluster configuration management. */
@HttpRpcResource(
    name = "ConfigRPCOps",
    description = "Cluster Configuration Management APIs",
    path = "config"
)
internal interface ConfigRPCOps : RpcOps, Closeable {
    // TODO - Joel - Describe.
    fun start(config: SmartConfig)

    /** Updates cluster configuration. */
    @HttpRpcPOST(description = "Update cluster configuration", path = "updateConfig")
    fun updateConfig(
        @HttpRpcRequestBodyParameter(description = "Details of the updated configuration", required = true)
        req: HTTPUpdateConfigRequest
    ): HTTPUpdateConfigResponse
}