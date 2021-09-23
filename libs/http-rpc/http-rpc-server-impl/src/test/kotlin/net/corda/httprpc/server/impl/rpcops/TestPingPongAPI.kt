package net.corda.httprpc.server.impl.rpcops

import net.corda.v5.base.annotations.CordaSerializable
import net.corda.v5.httprpc.api.RpcOps
import net.corda.v5.httprpc.api.annotations.HttpRpcPOST
import net.corda.v5.httprpc.api.annotations.HttpRpcRequestBodyParameter
import net.corda.v5.httprpc.api.annotations.HttpRpcResource

@HttpRpcResource(
    name = "API",
    description = "Health Check"
)
interface TestPingPongAPI : RpcOps {

    @HttpRpcPOST
    fun ping(
        @HttpRpcRequestBodyParameter(
            description = "Data",
            required = false
        ) data: PingPongData?
    ): String

    @CordaSerializable
    class PingPongData(val data: String)
}