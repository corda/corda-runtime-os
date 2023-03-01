package net.corda.rest.tools.annotations.validation.utils

import net.corda.rest.annotations.RestPathParameter
import net.corda.rest.annotations.RestQueryParameter
import net.corda.rest.annotations.ClientRequestBodyParameter
import net.corda.rest.annotations.isRestParameterAnnotation
import net.corda.rest.tools.annotations.extensions.name
import net.corda.rest.tools.isDuplexChannel
import java.lang.reflect.Parameter
import kotlin.reflect.full.createInstance

/**
 * The path parameter regex exposed from the HTTP-RPC module ("/{param}/")
 */
val pathParamRegex = "\\{([^/?]*)}".toRegex()

val String.asPathParam
    get() = "{$this}"

fun Parameter.isPathOrQueryParameter() =
    this.annotations.any { annotation -> annotation is RestPathParameter || annotation is RestQueryParameter }

fun Parameter.isBodyParameter() = (this.annotations.any { it is ClientRequestBodyParameter } || !this.isPathOrQueryParameter())
        && !this.type.isDuplexChannel()

@Suppress("ComplexMethod")
fun getParameterName(parameter: Parameter) =
    parameter.annotations.singleOrNull { it.isRestParameterAnnotation() }?.let {
        when (it) {
            is RestPathParameter -> it.name(parameter).lowercase()
            is RestQueryParameter -> it.name(parameter).lowercase()
            is ClientRequestBodyParameter -> it.name(parameter).lowercase()
            else -> throw IllegalArgumentException("Unknown parameter type")
        }
    } ?: ClientRequestBodyParameter::class.createInstance().name(parameter).lowercase()
