package net.corda.rest.client.processing

import net.corda.rest.annotations.HttpDELETE
import net.corda.rest.annotations.HttpGET
import net.corda.rest.annotations.HttpPOST
import net.corda.rest.annotations.HttpPUT
import net.corda.rest.annotations.isRestEndpointAnnotation
import net.corda.rest.tools.HttpVerb
import net.corda.rest.tools.isStaticallyExposedGet
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