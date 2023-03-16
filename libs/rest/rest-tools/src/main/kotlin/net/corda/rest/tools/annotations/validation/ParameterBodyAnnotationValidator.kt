package net.corda.rest.tools.annotations.validation

import net.corda.rest.RestResource
import net.corda.rest.annotations.HttpDELETE
import net.corda.rest.annotations.HttpGET
import net.corda.rest.annotations.HttpWS
import net.corda.rest.tools.annotations.validation.utils.isBodyParameter
import java.lang.reflect.Method

/**
 * Validates that every method annotated with [HttpGET], [HttpDELETE] or [HttpWS] does not contain a body.
 */
internal class ParameterBodyAnnotationValidator(private val clazz: Class<out RestResource>) : RestValidator {
    override fun validate(): RestValidationResult =
        clazz.methods.fold(RestValidationResult()) { total, method ->
            total + if (method.annotations.any { it is HttpGET || it is HttpDELETE || it is HttpWS }) {
                validateNoBodyParam(method)
            } else RestValidationResult()
        }

    private fun validateNoBodyParam(method: Method) =
        method.parameters.count { it.isBodyParameter() }.run {
            when (this) {
                0 -> RestValidationResult()
                else -> RestValidationResult(listOf("GET/DELETE/WS requests are not allowed to have a body"))
            }
        }
}