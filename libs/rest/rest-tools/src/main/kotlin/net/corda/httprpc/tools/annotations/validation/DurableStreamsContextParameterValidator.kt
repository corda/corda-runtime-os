package net.corda.httprpc.tools.annotations.validation

import net.corda.httprpc.RestResource
import net.corda.httprpc.annotations.HttpPOST
import net.corda.httprpc.annotations.HttpPUT
import net.corda.httprpc.tools.annotations.validation.utils.getParameterName
import net.corda.httprpc.tools.annotations.validation.utils.isBodyParameter
import net.corda.httprpc.durablestream.api.returnsDurableCursorBuilder
import java.lang.reflect.Method

/**
 * Validates that durable stream methods do not contain a "context" parameter, as it would clash with the implicitly created parameter with
 * the same name.
 */
class DurableStreamsContextParameterValidator(private val clazz: Class<out RestResource>) : RestValidator {

    companion object {
        const val error = "Methods returning DurableCursorBuilder or FiniteDurableCursorBuilder " +
                "are not allowed to have a body parameter with the name 'context'"
    }

    override fun validate(): RestValidationResult =
        clazz.methods.fold(RestValidationResult()) { total, method ->
            total + if (method.annotations.any { it is HttpPOST || it is HttpPUT }) {
                validateBodyParameterOnPOST(method)
            } else RestValidationResult()
        }

    private fun validateBodyParameterOnPOST(method: Method) =
        if (method.returnsDurableCursorBuilder()) {
            method.parameters.count { it.isBodyParameter() && getParameterName(it) == "context" }
                .run {
                    when (this) {
                        0 -> RestValidationResult()
                        else -> RestValidationResult(listOf(error))
                    }
                }
        } else RestValidationResult()
}
