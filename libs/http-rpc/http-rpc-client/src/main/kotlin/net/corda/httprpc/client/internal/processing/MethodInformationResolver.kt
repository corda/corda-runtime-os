package net.corda.httprpc.client.internal.processing

import net.corda.v5.httprpc.api.annotations.HttpRpcGET
import net.corda.v5.httprpc.api.annotations.HttpRpcPOST
import net.corda.v5.httprpc.tools.HttpVerb
import net.corda.v5.httprpc.tools.staticExposedGetMethods
import java.lang.reflect.Method

val Method.endpointHttpVerb: HttpVerb
    get() = this.annotations.singleOrNull { it is HttpRpcPOST || it is HttpRpcGET }.let {
        when {
            it is HttpRpcGET -> HttpVerb.GET
            it is HttpRpcPOST -> HttpVerb.POST
            staticExposedGetMethods.contains(this.name) -> HttpVerb.GET
            else -> throw IllegalArgumentException("Unknown endpoint type")
        }
    }