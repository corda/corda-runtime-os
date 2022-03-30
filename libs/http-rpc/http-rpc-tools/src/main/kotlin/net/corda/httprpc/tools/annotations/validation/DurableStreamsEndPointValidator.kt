package net.corda.httprpc.tools.annotations.validation

import net.corda.httprpc.RpcOps
import net.corda.httprpc.annotations.HttpRpcPOST
import net.corda.httprpc.annotations.HttpRpcPUT
import net.corda.v5.base.stream.returnsDurableCursorBuilder
import java.lang.reflect.Method

/**
 * Validates that durable stream methods are POST or PUT. This is required, as an implicit DurableStreamContext parameter
 * will be added to the call.
 */
internal class DurableStreamsEndPointValidator(private val clazz: Class<out RpcOps>) : HttpRpcValidator {

    companion object {
        val error = "Methods returning DurableCursorBuilder or FiniteDurableCursorBuilder " +
                "can only be exposed via ${HttpRpcPOST::class.simpleName} or ${HttpRpcPUT::class.simpleName}."
    }

    override fun validate(): HttpRpcValidationResult =
        clazz.methods.fold(HttpRpcValidationResult()) { total, method ->
            total + if (method.annotations.none { it is HttpRpcPOST || it is HttpRpcPUT }) {
                validateReturnTypeOnWrongMethod(method)
            } else HttpRpcValidationResult()
        }

    private fun validateReturnTypeOnWrongMethod(method: Method) =
        if (method.returnsDurableCursorBuilder()) {
            HttpRpcValidationResult(listOf(error))
        } else HttpRpcValidationResult()
}
