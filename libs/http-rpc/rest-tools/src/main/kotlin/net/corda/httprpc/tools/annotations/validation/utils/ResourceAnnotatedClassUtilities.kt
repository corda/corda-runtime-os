package net.corda.httprpc.tools.annotations.validation.utils

import net.corda.httprpc.RestResource
import net.corda.httprpc.annotations.HttpRpcDELETE
import net.corda.httprpc.annotations.HttpRpcGET
import net.corda.httprpc.annotations.HttpRpcPOST
import net.corda.httprpc.annotations.HttpRpcPUT
import net.corda.httprpc.annotations.HttpRpcPathParameter
import net.corda.httprpc.annotations.HttpRpcWS
import net.corda.httprpc.annotations.isRpcEndpointAnnotation
import net.corda.httprpc.tools.annotations.extensions.path
import net.corda.httprpc.tools.isStaticallyExposedGet
import java.lang.reflect.Method
import java.lang.reflect.Parameter
import kotlin.reflect.full.createInstance

internal val Class<out RestResource>.endpoints: List<Method>
    get() = this.methods.filter { method ->
        method.annotations.any { annotation ->
            annotation.isRpcEndpointAnnotation()
        } || method.isStaticallyExposedGet()
    }.sortedBy { it.name }

internal val List<Parameter>.pathParameters
    get() = this.filter { it.annotations.any { annotation -> annotation is HttpRpcPathParameter } }

internal fun Method.endpointPath(type: EndpointType): String? =
    when (type) {
        EndpointType.GET -> (this.annotations.singleOrNull { it is HttpRpcGET } as? HttpRpcGET)?.path(this)
            ?: HttpRpcGET::class.createInstance().path(this)
        EndpointType.POST -> (this.annotations.singleOrNull { it is HttpRpcPOST } as? HttpRpcPOST)?.path()
            ?: HttpRpcPOST::class.createInstance().path()
        EndpointType.PUT -> (this.annotations.singleOrNull { it is HttpRpcPUT } as? HttpRpcPUT)?.path()
            ?: HttpRpcPUT::class.createInstance().path()
        EndpointType.DELETE -> (this.annotations.singleOrNull { it is HttpRpcDELETE } as? HttpRpcDELETE)?.path()
            ?: HttpRpcDELETE::class.createInstance().path()
        EndpointType.WS -> (this.annotations.singleOrNull { it is HttpRpcWS } as? HttpRpcWS)?.path()
            ?: HttpRpcWS::class.createInstance().path()
    }

internal val Method.endpointType: EndpointType
    get() = this.annotations.firstOrNull { it.isRpcEndpointAnnotation() }?.let {
        when (it) {
            is HttpRpcGET -> EndpointType.GET
            is HttpRpcPOST -> EndpointType.POST
            is HttpRpcPUT -> EndpointType.PUT
            is HttpRpcDELETE -> EndpointType.DELETE
            is HttpRpcWS -> EndpointType.WS
            else -> throw IllegalArgumentException("Unknown endpoint type for: '$name'")
        }
    } ?: this.staticExposedEndpointType

private val Method.staticExposedEndpointType: EndpointType
    get() = if (isStaticallyExposedGet()) EndpointType.GET
    else throw IllegalArgumentException("Unknown statically exposed endpoint type for: '$name'")

internal enum class EndpointType {
    GET, POST, PUT, DELETE, WS
}