package net.corda.httprpc.tools.annotations.validation

import net.corda.httprpc.RestResource
import net.corda.httprpc.annotations.HttpRpcDELETE
import net.corda.httprpc.annotations.HttpRpcGET
import net.corda.httprpc.annotations.HttpRpcWS
import net.corda.httprpc.tools.annotations.validation.utils.isBodyParameter
import java.lang.reflect.Method

/**
 * Validates that every method annotated with [HttpRpcGET], [HttpRpcDELETE] or [HttpRpcWS] does not contain a body.
 */
internal class ParameterBodyAnnotationValidator(private val clazz: Class<out RestResource>) : HttpRpcValidator {
    override fun validate(): HttpRpcValidationResult =
        clazz.methods.fold(HttpRpcValidationResult()) { total, method ->
            total + if (method.annotations.any { it is HttpRpcGET || it is HttpRpcDELETE || it is HttpRpcWS }) {
                validateNoBodyParam(method)
            } else HttpRpcValidationResult()
        }

    private fun validateNoBodyParam(method: Method) =
        method.parameters.count { it.isBodyParameter() }.run {
            when (this) {
                0 -> HttpRpcValidationResult()
                else -> HttpRpcValidationResult(listOf("GET/DELETE/WS requests are not allowed to have a body"))
            }
        }
}