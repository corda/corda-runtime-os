package net.corda.httprpc.tools.annotations.validation

import net.corda.httprpc.RestResource
import net.corda.httprpc.annotations.HttpDELETE
import net.corda.httprpc.annotations.HttpGET
import net.corda.httprpc.annotations.HttpWS
import net.corda.httprpc.tools.annotations.validation.utils.isBodyParameter
import java.lang.reflect.Method

/**
 * Validates that every method annotated with [HttpGET], [HttpDELETE] or [HttpWS] does not contain a body.
 */
internal class ParameterBodyAnnotationValidator(private val clazz: Class<out RestResource>) : HttpRpcValidator {
    override fun validate(): HttpRpcValidationResult =
        clazz.methods.fold(HttpRpcValidationResult()) { total, method ->
            total + if (method.annotations.any { it is HttpGET || it is HttpDELETE || it is HttpWS }) {
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