package net.corda.httprpc.server.impl.rpcops

import net.corda.httprpc.RpcOps
import net.corda.httprpc.annotations.HttpRpcGET
import net.corda.httprpc.annotations.HttpRpcResource

@HttpRpcResource
interface TestDuplicateProtocolVersionAPI : RpcOps {
    @HttpRpcGET(path = "getProtocolVersion")
    fun test(): String
}