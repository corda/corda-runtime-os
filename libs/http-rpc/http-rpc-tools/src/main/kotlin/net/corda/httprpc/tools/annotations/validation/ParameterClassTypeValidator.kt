package net.corda.httprpc.tools.annotations.validation

import net.corda.httprpc.RestResource
import net.corda.httprpc.annotations.RestPathParameter
import net.corda.httprpc.annotations.RestQueryParameter
import net.corda.httprpc.tools.annotations.validation.utils.endpoints
import java.lang.reflect.Parameter

/**
 * Validates that every method path and query parameter is one of the expected types.
 */
internal class ParameterClassTypeValidator(private val clazz: Class<out RestResource>) : HttpRpcValidator {
    private val allowedPathParameterTypes = setOf(
        Int::class.java, Integer::class.java,
        Long::class.java, Long::class.javaObjectType,
        Boolean::class.java, Boolean::class.javaObjectType,
        String::class.java,
        Double::class.javaObjectType, Double::class.java
    )

    private val allowedQueryParameterTypes = allowedPathParameterTypes + List::class.java

    private fun Set<Class<out Any>>.membersAsString() =
        this.joinToString(prefix = "\"", postfix = "\"", separator = ", ") { it.simpleName }

    override fun validate(): HttpRpcValidationResult =
        clazz.endpoints.map { it.parameters.asList() }.fold(HttpRpcValidationResult()) { total, parameters ->
            total + getPathParameters(parameters).fold(HttpRpcValidationResult()) { pathTotal, next ->
                pathTotal + validateParameter(next, allowedPathParameterTypes)
            } + getQueryParameters(parameters).fold(HttpRpcValidationResult()) { queryTotal, next ->
                queryTotal + validateParameter(next, allowedQueryParameterTypes)
            }
        }

    private fun validateParameter(parameter: Parameter, allowedPathParameterTypes: Set<Class<out Any>>) =
        if (parameter.type !in allowedPathParameterTypes)
            HttpRpcValidationResult(
                listOf(
                    "Parameter type is not supported in ${clazz.simpleName} for: $parameter. " +
                            "Allowed parameter types are : ${allowedPathParameterTypes.membersAsString()}"
                )
            )
        else HttpRpcValidationResult()

    private fun getPathParameters(parameters: List<Parameter>) =
        parameters.filter {
            it.annotations.any { annotation ->
                annotation is RestPathParameter
            }
        }

    private fun getQueryParameters(parameters: List<Parameter>) =
        parameters.filter {
            it.annotations.any { annotation ->
                annotation is RestQueryParameter
            }
        }
}