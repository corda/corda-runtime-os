package net.corda.httprpc.server.apigen.test

import net.corda.v5.httprpc.api.RpcOps
import net.corda.v5.httprpc.api.annotations.HttpRpcPOST
import net.corda.v5.httprpc.api.annotations.HttpRpcQueryParameter
import net.corda.v5.httprpc.api.annotations.HttpRpcRequestBodyParameter
import net.corda.v5.httprpc.api.annotations.HttpRpcResource

@HttpRpcResource
interface MultipleParamAnnotationApi : RpcOps {
    override val protocolVersion: Int
        get() = 1

    @HttpRpcPOST
    fun test(@HttpRpcQueryParameter @HttpRpcRequestBodyParameter twoAnnotations: String)
}