package net.corda.httprpc.tools.annotations.validation

import net.corda.httprpc.RpcOps
import net.corda.httprpc.annotations.HttpRpcPOST
import net.corda.httprpc.tools.annotations.validation.utils.getParameterName
import net.corda.httprpc.tools.annotations.validation.utils.isBodyParameter
import net.corda.v5.base.stream.returnsDurableCursorBuilder
import java.lang.reflect.Method

/**
 * Validates that durable stream methods do not contain a "context" parameter, as it would clash with the implicitly created parameter with
 * the same name.
 */
class DurableStreamsContextParameterValidator(private val clazz: Class<out RpcOps>) : HttpRpcValidator {

    companion object {
        const val error = "Methods returning DurableCursorBuilder or FiniteDurableCursorBuilder " +
                "are not allowed to have a body parameter with the name 'context'"
    }

    override fun validate(): HttpRpcValidationResult =
        clazz.methods.fold(HttpRpcValidationResult()) { total, method ->
            total + if (method.annotations.any { it is HttpRpcPOST }) {
                validateBodyParameterOnPOST(method)
            } else HttpRpcValidationResult()
        }

    private fun validateBodyParameterOnPOST(method: Method) =
        if (method.returnsDurableCursorBuilder()) {
            method.parameters.count { it.isBodyParameter() && getParameterName(it) == "context" }
                .run {
                    when (this) {
                        0 -> HttpRpcValidationResult()
                        else -> HttpRpcValidationResult(listOf(error))
                    }
                }
        } else HttpRpcValidationResult()
}
