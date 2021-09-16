package net.corda.httprpc.server.apigen.test

import net.corda.v5.httprpc.api.RpcOps
import net.corda.v5.httprpc.api.annotations.HttpRpcPOST
import net.corda.v5.httprpc.api.annotations.HttpRpcResource

@HttpRpcResource(path = "nonCordaSerializable")
interface NonCordaSerializableAPI : RpcOps {
    @HttpRpcPOST
    fun call(data: NonCordaSerializableClass): String
}

class NonCordaSerializableClass(
    val data: String
)