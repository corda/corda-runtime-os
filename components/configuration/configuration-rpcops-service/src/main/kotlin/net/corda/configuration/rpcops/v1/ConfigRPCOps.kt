package net.corda.configuration.rpcops.v1

import net.corda.configuration.rpcops.v1.types.ConfigResponseType
import net.corda.configuration.rpcops.v1.types.UpdateConfigType
import net.corda.httprpc.RpcOps
import net.corda.httprpc.annotations.HttpRpcPOST
import net.corda.httprpc.annotations.HttpRpcRequestBodyParameter
import net.corda.httprpc.annotations.HttpRpcResource

/** RPC operations for cluster configuration management. */
@HttpRpcResource(
    name = "ConfigRPCOps",
    description = "Cluster Configuration Management APIs",
    path = "config"
)
interface ConfigRPCOps : RpcOps {
    /** Updates cluster configuration. */
    @HttpRpcPOST(description = "Update cluster configuration", path = "updateConfig")
    fun updateConfig(
        @HttpRpcRequestBodyParameter(description = "Details of the updated configuration", required = true)
        updateConfigType: UpdateConfigType
    ): ConfigResponseType
}