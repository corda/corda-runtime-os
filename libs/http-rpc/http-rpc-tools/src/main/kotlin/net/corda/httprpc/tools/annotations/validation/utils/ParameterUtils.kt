package net.corda.httprpc.tools.annotations.validation.utils

import net.corda.httprpc.tools.annotations.extensions.name
import net.corda.v5.httprpc.api.annotations.HttpRpcPathParameter
import net.corda.v5.httprpc.api.annotations.HttpRpcQueryParameter
import net.corda.v5.httprpc.api.annotations.HttpRpcRequestBodyParameter
import net.corda.v5.httprpc.api.annotations.isHttpRpcParameterAnnotation
import java.lang.reflect.Parameter
import kotlin.reflect.full.createInstance

/**
 * The path parameter regex exposed from the HTTP-RPC module ("/{param}/")
 */
val pathParamRegex = "\\{([^/?]*)}".toRegex()

val String.asPathParam
    get() = "{$this}"

fun Parameter.isPathOrQueryParameter() =
    this.annotations.any { annotation -> annotation is HttpRpcPathParameter || annotation is HttpRpcQueryParameter }

fun Parameter.isBodyParameter() = this.annotations.any { it is HttpRpcRequestBodyParameter } || !this.isPathOrQueryParameter()

@Suppress("ComplexMethod")
fun getParameterName(parameter: Parameter) =
    parameter.annotations.singleOrNull { it.isHttpRpcParameterAnnotation() }?.let {
        when (it) {
            is HttpRpcPathParameter -> it.name(parameter).toLowerCase()
            is HttpRpcQueryParameter -> it.name(parameter).toLowerCase()
            is HttpRpcRequestBodyParameter -> it.name(parameter).toLowerCase()
            else -> throw IllegalArgumentException("Unknown parameter type")
        }
    } ?: HttpRpcRequestBodyParameter::class.createInstance().name(parameter).toLowerCase()