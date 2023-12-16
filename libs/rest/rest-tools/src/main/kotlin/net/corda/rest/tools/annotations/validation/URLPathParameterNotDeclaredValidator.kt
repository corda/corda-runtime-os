package net.corda.rest.tools.annotations.validation

import net.corda.rest.RestResource
import net.corda.rest.annotations.RestPathParameter
import net.corda.rest.tools.annotations.validation.utils.endpointPath
import net.corda.rest.tools.annotations.validation.utils.endpointType
import net.corda.rest.tools.annotations.validation.utils.endpoints
import net.corda.rest.tools.annotations.validation.utils.pathParamRegex
import net.corda.rest.tools.annotations.validation.utils.pathParameters
import java.lang.reflect.Method
import java.lang.reflect.Parameter

/**
 * Validates that every endpoint path parameter is present as a method path parameter.
 */
internal class URLPathParameterNotDeclaredValidator(private val clazz: Class<out RestResource>) : RestValidator {

    override fun validate(): RestValidationResult =
        clazz.endpoints.fold(RestValidationResult()) { total, method ->
            total + validatePath(method, method.parameters.asList())
        }

    private fun validatePath(method: Method, params: List<Parameter>): RestValidationResult {
        val endpointPath = method.endpointPath(method.endpointType)
        return endpointPath?.let { path ->
            pathParamRegex.findAll(path).fold(RestValidationResult()) { total, next ->
                val pathParamNameInURL = next.groupValues[1]
                val existsInFunction = params.pathParameters.any { getParameterName(it).equals(pathParamNameInURL, ignoreCase = true) }
                total + if (existsInFunction) {
                    RestValidationResult()
                } else {
                    RestValidationResult(listOf("Path parameter $pathParamNameInURL does not exist in function signature"))
                }
            }
        } ?: RestValidationResult()
    }

    @Suppress("ComplexMethod")
    private fun getParameterName(parameter: Parameter) =
        parameter.annotations.single { it is RestPathParameter }?.let { annotation ->
            (annotation as RestPathParameter).name.lowercase().takeIf { name -> name.isNotBlank() }
                ?: parameter.name.lowercase()
        }
}
