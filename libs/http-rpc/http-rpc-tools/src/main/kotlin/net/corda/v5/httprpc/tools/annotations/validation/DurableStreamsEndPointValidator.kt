package net.corda.v5.httprpc.tools.annotations.validation

import net.corda.v5.base.stream.returnsDurableCursorBuilder
import net.corda.httprpc.RpcOps
import net.corda.httprpc.annotations.HttpRpcGET
import net.corda.httprpc.annotations.HttpRpcPOST
import java.lang.reflect.Method

/**
 * Validates that durable stream methods are POST. This is required, as an implicit DurableStreamContext parameter will be added to the
 * call.
 */
internal class DurableStreamsEndPointValidator(private val clazz: Class<out RpcOps>) : HttpRpcValidator {

    companion object {
        val error = "Methods returning DurableCursorBuilder or FiniteDurableCursorBuilder " +
                "can only be exposed via ${HttpRpcPOST::class.simpleName}."
    }

    override fun validate(): HttpRpcValidationResult =
        clazz.methods.fold(HttpRpcValidationResult()) { total, method ->
            total + if (method.annotations.any { it is HttpRpcGET }) {
                validateReturnTypeOnGET(method)
            } else HttpRpcValidationResult()
        }

    private fun validateReturnTypeOnGET(method: Method) =
        if (method.returnsDurableCursorBuilder()) {
            HttpRpcValidationResult(listOf(error))
        } else HttpRpcValidationResult()
}
