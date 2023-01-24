package net.corda.httprpc.client.processing

import net.corda.httprpc.annotations.HttpDELETE
import net.corda.httprpc.annotations.HttpGET
import net.corda.httprpc.annotations.HttpPOST
import net.corda.httprpc.annotations.HttpPUT
import net.corda.httprpc.annotations.isRestEndpointAnnotation
import net.corda.httprpc.tools.HttpVerb
import net.corda.httprpc.tools.isStaticallyExposedGet
import java.lang.reflect.Method

internal val Method.endpointHttpVerb: HttpVerb
    get() = this.annotations.singleOrNull { it.isRestEndpointAnnotation() }.let {
        when {
            it is HttpGET -> HttpVerb.GET
            it is HttpPOST -> HttpVerb.POST
            it is HttpPUT -> HttpVerb.PUT
            it is HttpDELETE -> HttpVerb.DELETE
            isStaticallyExposedGet() -> HttpVerb.GET
            else -> throw IllegalArgumentException("Unknown endpoint type")
        }
    }