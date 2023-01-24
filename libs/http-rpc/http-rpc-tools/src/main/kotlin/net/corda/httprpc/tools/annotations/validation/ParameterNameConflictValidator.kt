package net.corda.httprpc.tools.annotations.validation

import net.corda.httprpc.RestResource
import net.corda.httprpc.annotations.HttpRpcPathParameter
import net.corda.httprpc.annotations.HttpRpcQueryParameter
import net.corda.httprpc.annotations.HttpRpcRequestBodyParameter
import net.corda.httprpc.annotations.isHttpRpcParameterAnnotation
import net.corda.httprpc.tools.annotations.validation.utils.endpoints
import net.corda.httprpc.tools.annotations.validation.utils.getParameterName
import java.lang.reflect.Parameter

/**
 * Validates that parameter names of same-type parameters in the same method do not clash.
 */
internal class ParameterNameConflictValidator(private val clazz: Class<out RestResource>) : HttpRpcValidator {
    override fun validate(): HttpRpcValidationResult =
        clazz.endpoints.fold(HttpRpcValidationResult()) { total, method ->
            total + validateSameTypeParameters(method.parameters.asList())
        }

    private fun validateSameTypeParameters(params: List<Parameter>): HttpRpcValidationResult {
        val namesToTypes = mutableSetOf<Pair<String, String>>()
        return params.fold(HttpRpcValidationResult()) { total, param ->
            val parameterType = getParameterType(param)
            total + namesToTypes.validateNoDuplicateName(param, parameterType)
        }
    }

    private fun MutableSet<Pair<String, String>>.validateNoDuplicateName(
        parameter: Parameter,
        parameterType: String
    ): HttpRpcValidationResult {
        val parameterName = getParameterName(parameter)
        return if (this.contains(parameterName to parameterType)) {
            HttpRpcValidationResult(
                listOf("Duplicate parameter name: '$parameterName' on parameter '${parameter.name} of type '$parameterType'")
            )
        } else {
            this.add(parameterName to parameterType)
            HttpRpcValidationResult()
        }
    }

    private fun getParameterType(param: Parameter): String =
        param.annotations.singleOrNull { it.isHttpRpcParameterAnnotation() }?.let {
            when (it) {
                is HttpRpcPathParameter -> "PATH"
                is HttpRpcQueryParameter -> "QUERY"
                is HttpRpcRequestBodyParameter -> "BODY"
                else -> throw IllegalArgumentException("Unknown parameter type")
            }
        } ?: "BODY"
}