package net.corda.v5.httprpc.tools.annotations.validation

import net.corda.v5.application.messaging.RPCOps
import net.corda.v5.httprpc.api.annotations.HttpRpcGET
import net.corda.v5.httprpc.api.annotations.HttpRpcPOST
import java.lang.reflect.Method

/**
 * Validates that every method is not annotated with both [HttpRpcPOST] and [HttpRpcGET].
 */
internal class EndpointAnnotationValidator(private val clazz: Class<out RPCOps>) : HttpRpcValidator {

    companion object {
        fun error(method: Method) = "Only one of ${HttpRpcPOST::class.simpleName}, ${HttpRpcGET::class.simpleName} " +
                "can be specified on method ${method.name}."
    }

    override fun validate(): HttpRpcValidationResult =
        clazz.methods.fold(HttpRpcValidationResult()) { total, method ->
            total + method.annotations.count { annotation -> annotation is HttpRpcPOST || annotation is HttpRpcGET }.run {
                when (this) {
                    1 -> HttpRpcValidationResult(listOf())
                    0 -> HttpRpcValidationResult(listOf())
                    else -> HttpRpcValidationResult(listOf(error(method)))
                }
            }
        }
}