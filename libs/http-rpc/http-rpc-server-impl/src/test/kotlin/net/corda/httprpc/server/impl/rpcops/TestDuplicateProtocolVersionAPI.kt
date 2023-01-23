package net.corda.httprpc.server.impl.rpcops

import net.corda.httprpc.RestResource
import net.corda.httprpc.annotations.HttpRpcGET
import net.corda.httprpc.annotations.HttpRpcResource

@HttpRpcResource
interface TestDuplicateProtocolVersionAPI : RestResource {
    @HttpRpcGET(path = "getProtocolVersion")
    fun test(): String
}