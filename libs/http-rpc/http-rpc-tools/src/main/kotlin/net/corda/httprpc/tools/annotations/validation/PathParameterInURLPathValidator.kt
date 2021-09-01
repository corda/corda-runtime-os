package net.corda.httprpc.tools.annotations.validation

import net.corda.httprpc.tools.annotations.extensions.name
import net.corda.httprpc.tools.annotations.validation.utils.asPathParam
import net.corda.httprpc.tools.annotations.validation.utils.endpointPath
import net.corda.httprpc.tools.annotations.validation.utils.endpointType
import net.corda.httprpc.tools.annotations.validation.utils.endpoints
import net.corda.httprpc.tools.annotations.validation.utils.pathParameters
import net.corda.v5.application.messaging.RPCOps
import net.corda.v5.httprpc.api.annotations.HttpRpcPathParameter
import java.lang.reflect.Method
import java.lang.reflect.Parameter

/**
 * Validates that every method path parameter is present in the endpoint path.
 */
internal class PathParameterInURLPathValidator(private val clazz: Class<out RPCOps>) : HttpRpcValidator {
    override fun validate(): HttpRpcValidationResult =
        clazz.endpoints.fold(HttpRpcValidationResult()) { total, method ->
            total + validateParameters(method, method.parameters.asList())
        }

    private fun validateParameters(method: Method, params: List<Parameter>): HttpRpcValidationResult {
        return params.pathParameters.fold(HttpRpcValidationResult()) { total, param ->
            val endpointPath = method.endpointPath(method.endpointType)
            total + (validateParameter(endpointPath, param))
        }
    }

    private fun validateParameter(path: String, parameter: Parameter): HttpRpcValidationResult {
        val parameterName = getParameterName(parameter)
        val exists = parameterName!!.toLowerCase().existsInPath(path.toLowerCase())
        return if (exists) HttpRpcValidationResult()
        else HttpRpcValidationResult(listOf("Path parameter '$parameterName' does not exist in endpoint path '$path'"))
    }

    private fun String.existsInPath(path: String): Boolean {
        return path.contains(this.asPathParam)
    }

    @Suppress("ComplexMethod")
    private fun getParameterName(parameter: Parameter) =
        parameter.annotations.single { it is HttpRpcPathParameter }?.let { annotation ->
            (annotation as HttpRpcPathParameter).name(parameter)
        }
}