package net.corda.httprpc.tools.annotations.validation

import net.corda.httprpc.RestResource
import net.corda.httprpc.annotations.isRestEndpointAnnotation
import java.lang.reflect.Method

/**
 * Validates that every method has no more than one HTTP RPC endpoint annotation.
 */
internal class EndpointAnnotationValidator(private val clazz: Class<out RestResource>) : HttpRpcValidator {

    companion object {
        fun error(method: Method) = "Only one of HttpRpc endpoint annotations can be applied on method ${method.name}."
    }

    override fun validate(): HttpRpcValidationResult =
        clazz.methods.fold(HttpRpcValidationResult()) { total, method ->
            total + method.annotations.count { annotation -> annotation.isRestEndpointAnnotation() }.run {
                when (this) {
                    1 -> HttpRpcValidationResult(listOf())
                    0 -> HttpRpcValidationResult(listOf())
                    else -> HttpRpcValidationResult(listOf(error(method)))
                }
            }
        }
}