package net.corda.v5.httprpc.tools.annotations.validation

import net.corda.httprpc.RpcOps
import net.corda.httprpc.annotations.HttpRpcPathParameter
import net.corda.v5.httprpc.tools.annotations.validation.utils.endpointPath
import net.corda.v5.httprpc.tools.annotations.validation.utils.endpointType
import net.corda.v5.httprpc.tools.annotations.validation.utils.endpoints
import net.corda.v5.httprpc.tools.annotations.validation.utils.pathParamRegex
import net.corda.v5.httprpc.tools.annotations.validation.utils.pathParameters
import java.lang.reflect.Method
import java.lang.reflect.Parameter

/**
 * Validates that every endpoint path parameter is present as a method path parameter.
 */
internal class URLPathParameterNotDeclaredValidator(private val clazz: Class<out RpcOps>) : HttpRpcValidator {

    override fun validate(): HttpRpcValidationResult =
        clazz.endpoints.fold(HttpRpcValidationResult()) { total, method ->
            total + validatePath(method, method.parameters.asList())
        }

    private fun validatePath(method: Method, params: List<Parameter>): HttpRpcValidationResult {
        return method.endpointPath(method.endpointType).let { path ->
            pathParamRegex.findAll(path).fold(HttpRpcValidationResult()) { total, next ->
                val pathParamNameInURL = next.groupValues[1]
                val existsInFunction = params.pathParameters.any { getParameterName(it).equals(pathParamNameInURL, ignoreCase = true) }
                total + if (existsInFunction) HttpRpcValidationResult()
                else HttpRpcValidationResult(listOf("Path parameter $pathParamNameInURL does not exist in function signature"))
            }
        }
    }

    @Suppress("ComplexMethod")
    private fun getParameterName(parameter: Parameter) =
        parameter.annotations.single { it is HttpRpcPathParameter }?.let { annotation ->
            (annotation as HttpRpcPathParameter).name.toLowerCase().takeIf { name -> name.isNotBlank() }
                ?: parameter.name.toLowerCase()
        }
}