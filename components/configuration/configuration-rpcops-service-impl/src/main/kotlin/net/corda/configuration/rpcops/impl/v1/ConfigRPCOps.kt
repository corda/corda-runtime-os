package net.corda.configuration.rpcops.impl.v1

import net.corda.configuration.rpcops.impl.v1.types.HTTPUpdateConfigRequest
import net.corda.configuration.rpcops.impl.v1.types.HTTPUpdateConfigResponse
import net.corda.data.config.ConfigurationManagementRequest
import net.corda.data.config.ConfigurationManagementResponse
import net.corda.httprpc.RpcOps
import net.corda.httprpc.annotations.HttpRpcPOST
import net.corda.httprpc.annotations.HttpRpcRequestBodyParameter
import net.corda.httprpc.annotations.HttpRpcResource
import net.corda.messaging.api.publisher.RPCSender
import java.io.Closeable

/** RPC operations for cluster configuration management. */
@HttpRpcResource(
    name = "ConfigRPCOps",
    description = "Cluster Configuration Management APIs",
    path = "config"
)
interface ConfigRPCOps : RpcOps, Closeable {
    var rpcSender: RPCSender<ConfigurationManagementRequest, ConfigurationManagementResponse>?

    /** Updates cluster configuration. */
    @HttpRpcPOST(description = "Update cluster configuration", path = "updateConfig")
    fun updateConfig(
        @HttpRpcRequestBodyParameter(description = "Details of the updated configuration", required = true)
        req: HTTPUpdateConfigRequest
    ): HTTPUpdateConfigResponse
}