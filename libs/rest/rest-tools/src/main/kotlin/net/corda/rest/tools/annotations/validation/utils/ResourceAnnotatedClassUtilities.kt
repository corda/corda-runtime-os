package net.corda.rest.tools.annotations.validation.utils

import net.corda.rest.RestResource
import net.corda.rest.annotations.HttpDELETE
import net.corda.rest.annotations.HttpGET
import net.corda.rest.annotations.HttpPOST
import net.corda.rest.annotations.HttpPUT
import net.corda.rest.annotations.RestPathParameter
import net.corda.rest.annotations.HttpWS
import net.corda.rest.annotations.isRestEndpointAnnotation
import net.corda.rest.tools.annotations.extensions.path
import net.corda.rest.tools.isStaticallyExposedGet
import java.lang.reflect.Method
import java.lang.reflect.Parameter
import kotlin.reflect.full.createInstance

internal val Class<out RestResource>.endpoints: List<Method>
    get() = this.methods.filter { method ->
        method.annotations.any { annotation ->
            annotation.isRestEndpointAnnotation()
        } || method.isStaticallyExposedGet()
    }.sortedBy { it.name }

internal val List<Parameter>.pathParameters
    get() = this.filter { it.annotations.any { annotation -> annotation is RestPathParameter } }

internal fun Method.endpointPath(type: EndpointType): String? =
    when (type) {
        EndpointType.GET -> (this.annotations.singleOrNull { it is HttpGET } as? HttpGET)?.path(this)
            ?: HttpGET::class.createInstance().path(this)
        EndpointType.POST -> (this.annotations.singleOrNull { it is HttpPOST } as? HttpPOST)?.path()
            ?: HttpPOST::class.createInstance().path()
        EndpointType.PUT -> (this.annotations.singleOrNull { it is HttpPUT } as? HttpPUT)?.path()
            ?: HttpPUT::class.createInstance().path()
        EndpointType.DELETE -> (this.annotations.singleOrNull { it is HttpDELETE } as? HttpDELETE)?.path()
            ?: HttpDELETE::class.createInstance().path()
        EndpointType.WS -> (this.annotations.singleOrNull { it is HttpWS } as? HttpWS)?.path()
            ?: HttpWS::class.createInstance().path()
    }

internal val Method.endpointType: EndpointType
    get() = this.annotations.firstOrNull { it.isRestEndpointAnnotation() }?.let {
        when (it) {
            is HttpGET -> EndpointType.GET
            is HttpPOST -> EndpointType.POST
            is HttpPUT -> EndpointType.PUT
            is HttpDELETE -> EndpointType.DELETE
            is HttpWS -> EndpointType.WS
            else -> throw IllegalArgumentException("Unknown endpoint type for: '$name'")
        }
    } ?: this.staticExposedEndpointType

private val Method.staticExposedEndpointType: EndpointType
    get() = if (isStaticallyExposedGet()) EndpointType.GET
    else throw IllegalArgumentException("Unknown statically exposed endpoint type for: '$name'")

internal enum class EndpointType {
    GET, POST, PUT, DELETE, WS
}