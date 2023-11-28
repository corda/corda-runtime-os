package net.corda.rest.tools.annotations.validation

import net.corda.rest.RestResource
import net.corda.rest.annotations.ClientRequestBodyParameter
import net.corda.rest.annotations.RestPathParameter
import net.corda.rest.annotations.RestQueryParameter
import net.corda.rest.annotations.isRestParameterAnnotation
import net.corda.rest.tools.annotations.validation.utils.endpoints
import net.corda.rest.tools.annotations.validation.utils.getParameterName
import java.lang.reflect.Parameter

/**
 * Validates that parameter names of same-type parameters in the same method do not clash.
 */
internal class ParameterNameConflictValidator(private val clazz: Class<out RestResource>) : RestValidator {
    override fun validate(): RestValidationResult =
        clazz.endpoints.fold(RestValidationResult()) { total, method ->
            total + validateSameTypeParameters(method.parameters.asList())
        }

    private fun validateSameTypeParameters(params: List<Parameter>): RestValidationResult {
        val namesToTypes = mutableSetOf<Pair<String, String>>()
        return params.fold(RestValidationResult()) { total, param ->
            val parameterType = getParameterType(param)
            total + namesToTypes.validateNoDuplicateName(param, parameterType)
        }
    }

    private fun MutableSet<Pair<String, String>>.validateNoDuplicateName(
        parameter: Parameter,
        parameterType: String
    ): RestValidationResult {
        val parameterName = getParameterName(parameter)
        return if (this.contains(parameterName to parameterType)) {
            RestValidationResult(
                listOf("Duplicate parameter name: '$parameterName' on parameter '${parameter.name} of type '$parameterType'")
            )
        } else {
            this.add(parameterName to parameterType)
            RestValidationResult()
        }
    }

    private fun getParameterType(param: Parameter): String =
        param.annotations.singleOrNull { it.isRestParameterAnnotation() }?.let {
            when (it) {
                is RestPathParameter -> "PATH"
                is RestQueryParameter -> "QUERY"
                is ClientRequestBodyParameter -> "BODY"
                else -> throw IllegalArgumentException("Unknown parameter type")
            }
        } ?: "BODY"
}
