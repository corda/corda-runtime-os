package net.corda.httprpc.server.impl.rpcops

import net.corda.httprpc.RestResource
import net.corda.httprpc.annotations.HttpRpcPOST
import net.corda.httprpc.annotations.HttpRpcQueryParameter
import net.corda.httprpc.annotations.HttpRpcRequestBodyParameter
import net.corda.httprpc.annotations.HttpRpcResource

@HttpRpcResource
interface MultipleParamAnnotationApi : RestResource {
    override val protocolVersion: Int
        get() = 1

    @HttpRpcPOST
    fun test(@HttpRpcQueryParameter @HttpRpcRequestBodyParameter twoAnnotations: String)
}