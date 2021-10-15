package net.corda.httprpc.client.processing

import net.corda.httprpc.annotations.HttpRpcGET
import net.corda.httprpc.annotations.HttpRpcPOST
import net.corda.httprpc.tools.HttpVerb
import net.corda.httprpc.tools.staticExposedGetMethods
import java.lang.reflect.Method

internal val Method.endpointHttpVerb: HttpVerb
    get() = this.annotations.singleOrNull { it is HttpRpcPOST || it is HttpRpcGET }.let {
        when {
            it is HttpRpcGET -> HttpVerb.GET
            it is HttpRpcPOST -> HttpVerb.POST
            staticExposedGetMethods.contains(this.name) -> HttpVerb.GET
            else -> throw IllegalArgumentException("Unknown endpoint type")
        }
    }