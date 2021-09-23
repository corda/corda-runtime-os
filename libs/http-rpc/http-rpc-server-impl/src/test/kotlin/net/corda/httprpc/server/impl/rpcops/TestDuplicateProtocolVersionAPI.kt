package net.corda.httprpc.server.impl.rpcops

import net.corda.v5.httprpc.api.RpcOps
import net.corda.v5.httprpc.api.annotations.HttpRpcGET
import net.corda.v5.httprpc.api.annotations.HttpRpcResource

@HttpRpcResource
interface TestDuplicateProtocolVersionAPI : RpcOps {
    @HttpRpcGET(path = "getProtocolVersion")
    fun test(): String
}