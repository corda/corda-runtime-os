package net.corda.httprpc.tools.annotations.validation

import net.corda.httprpc.RestResource
import net.corda.httprpc.annotations.HttpRpcResource

/**
 * Validates that every class validated is annotated with [HttpRpcResource]
 */
internal class ResourceAnnotationValidator(private val clazz: Class<out RestResource>) : HttpRpcValidator {

    companion object {
        val error = "RestResource interface must be annotated with ${HttpRpcResource::class.qualifiedName}."
    }

    override fun validate(): HttpRpcValidationResult =
        clazz.annotations.find { annotation -> annotation is HttpRpcResource }?.let { HttpRpcValidationResult() }
            ?: HttpRpcValidationResult(listOf(error))
}