package net.corda.httprpc.client.processing

import net.corda.httprpc.annotations.HttpRpcDELETE
import net.corda.httprpc.annotations.HttpRpcGET
import net.corda.httprpc.annotations.HttpRpcPOST
import net.corda.httprpc.annotations.HttpRpcPUT
import net.corda.httprpc.annotations.isRpcEndpointAnnotation
import net.corda.httprpc.tools.HttpVerb
import net.corda.httprpc.tools.isStaticallyExposedGet
import java.lang.reflect.Method

internal val Method.endpointHttpVerb: HttpVerb
    get() = this.annotations.singleOrNull { it.isRpcEndpointAnnotation() }.let {
        when {
            it is HttpRpcGET -> HttpVerb.GET
            it is HttpRpcPOST -> HttpVerb.POST
            it is HttpRpcPUT -> HttpVerb.PUT
            it is HttpRpcDELETE -> HttpVerb.DELETE
            isStaticallyExposedGet() -> HttpVerb.GET
            else -> throw IllegalArgumentException("Unknown endpoint type")
        }
    }