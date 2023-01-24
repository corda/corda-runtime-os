package net.corda.httprpc.tools.annotations.validation

import net.corda.httprpc.RestResource
import net.corda.httprpc.annotations.HttpRpcPathParameter
import net.corda.httprpc.annotations.HttpRpcQueryParameter
import net.corda.httprpc.tools.annotations.validation.utils.endpoints
import java.lang.reflect.Parameter

/**
 * Validates that every method path and query parameter is one of the expected types.
 */
internal class ParameterClassTypeValidator(private val clazz: Class<out RestResource>) : RestValidator {
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

    override fun validate(): RestValidationResult =
        clazz.endpoints.map { it.parameters.asList() }.fold(RestValidationResult()) { total, parameters ->
            total + getPathParameters(parameters).fold(RestValidationResult()) { pathTotal, next ->
                pathTotal + validateParameter(next, allowedPathParameterTypes)
            } + getQueryParameters(parameters).fold(RestValidationResult()) { queryTotal, next ->
                queryTotal + validateParameter(next, allowedQueryParameterTypes)
            }
        }

    private fun validateParameter(parameter: Parameter, allowedPathParameterTypes: Set<Class<out Any>>) =
        if (parameter.type !in allowedPathParameterTypes)
            RestValidationResult(
                listOf(
                    "Parameter type is not supported in ${clazz.simpleName} for: $parameter. " +
                            "Allowed parameter types are : ${allowedPathParameterTypes.membersAsString()}"
                )
            )
        else RestValidationResult()

    private fun getPathParameters(parameters: List<Parameter>) =
        parameters.filter {
            it.annotations.any { annotation ->
                annotation is HttpRpcPathParameter
            }
        }

    private fun getQueryParameters(parameters: List<Parameter>) =
        parameters.filter {
            it.annotations.any { annotation ->
                annotation is HttpRpcQueryParameter
            }
        }
}