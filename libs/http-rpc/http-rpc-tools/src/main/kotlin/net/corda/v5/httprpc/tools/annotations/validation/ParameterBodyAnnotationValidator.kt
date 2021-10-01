package net.corda.v5.httprpc.tools.annotations.validation

import net.corda.httprpc.RpcOps
import net.corda.httprpc.annotations.HttpRpcGET
import net.corda.v5.httprpc.tools.annotations.validation.utils.isBodyParameter
import java.lang.reflect.Method

/**
 * Validates that every method annotated with [HttpRpcGET] does not contain a body.
 */
internal class ParameterBodyAnnotationValidator(private val clazz: Class<out RpcOps>) : HttpRpcValidator {
    override fun validate(): HttpRpcValidationResult =
        clazz.methods.fold(HttpRpcValidationResult()) { total, method ->
            total + if (method.annotations.any { it is HttpRpcGET }) {
                validateBodyOnGET(method)
            } else HttpRpcValidationResult()
        }

    private fun validateBodyOnGET(method: Method) =
        method.parameters.count { it.isBodyParameter() }.run {
            when (this) {
                0 -> HttpRpcValidationResult()
                else -> HttpRpcValidationResult(listOf("GET requests are not allowed to have a body"))
            }
        }
}