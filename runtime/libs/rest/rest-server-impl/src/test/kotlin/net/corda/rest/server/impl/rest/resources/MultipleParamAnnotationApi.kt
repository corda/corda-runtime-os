package net.corda.rest.server.impl.rest.resources

import net.corda.rest.RestResource
import net.corda.rest.annotations.HttpPOST
import net.corda.rest.annotations.RestQueryParameter
import net.corda.rest.annotations.ClientRequestBodyParameter
import net.corda.rest.annotations.HttpRestResource

@HttpRestResource
interface MultipleParamAnnotationApi : RestResource {
    override val protocolVersion: Int
        get() = 1

    @HttpPOST
    fun test(@RestQueryParameter @ClientRequestBodyParameter twoAnnotations: String)
}