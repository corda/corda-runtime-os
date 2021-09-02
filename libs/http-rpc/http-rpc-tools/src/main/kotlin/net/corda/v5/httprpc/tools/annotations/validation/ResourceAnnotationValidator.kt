package net.corda.v5.httprpc.tools.annotations.validation

import net.corda.v5.httprpc.api.RpcOps
import net.corda.v5.httprpc.api.annotations.HttpRpcResource

/**
 * Validates that every class validated is annotated with [HttpRpcResource]
 */
internal class ResourceAnnotationValidator(private val clazz: Class<out RpcOps>) : HttpRpcValidator {

    companion object {
        val error = "RpcOps interface must be annotated with ${HttpRpcResource::class.qualifiedName}."
    }

    override fun validate(): HttpRpcValidationResult =
        clazz.annotations.find { annotation -> annotation is HttpRpcResource }?.let { HttpRpcValidationResult() }
            ?: HttpRpcValidationResult(listOf(error))
}