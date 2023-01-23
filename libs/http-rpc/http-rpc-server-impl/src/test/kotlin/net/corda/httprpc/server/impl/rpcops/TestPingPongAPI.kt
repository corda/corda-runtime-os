package net.corda.httprpc.server.impl.rpcops

import net.corda.httprpc.RestResource
import net.corda.httprpc.annotations.HttpRpcPOST
import net.corda.httprpc.annotations.HttpRpcRequestBodyParameter
import net.corda.httprpc.annotations.HttpRpcResource

@HttpRpcResource(
    name = "API",
    description = "Health Check"
)
interface TestPingPongAPI : RestResource {

    @HttpRpcPOST
    fun ping(
        @HttpRpcRequestBodyParameter(
            description = "Data",
            required = false
        ) data: PingPongData?
    ): String

    class PingPongData(val data: String)
}