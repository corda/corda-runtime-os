package net.corda.httprpc.server.impl.rpcops

import net.corda.httprpc.RestResource
import net.corda.httprpc.annotations.HttpPOST
import net.corda.httprpc.annotations.RestQueryParameter
import net.corda.httprpc.annotations.RestRequestBodyParameter
import net.corda.httprpc.annotations.HttpRestResource

@HttpRestResource
interface MultipleParamAnnotationApi : RestResource {
    override val protocolVersion: Int
        get() = 1

    @HttpPOST
    fun test(@RestQueryParameter @RestRequestBodyParameter twoAnnotations: String)
}