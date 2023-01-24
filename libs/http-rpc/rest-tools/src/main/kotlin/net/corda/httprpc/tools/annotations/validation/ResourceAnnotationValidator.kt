package net.corda.httprpc.tools.annotations.validation

import net.corda.httprpc.RestResource
import net.corda.httprpc.annotations.HttpRestResource

/**
 * Validates that every class validated is annotated with [HttpRestResource]
 */
internal class ResourceAnnotationValidator(private val clazz: Class<out RestResource>) : RestValidator {

    companion object {
        val error = "RestResource interface must be annotated with ${HttpRestResource::class.qualifiedName}."
    }

    override fun validate(): RestValidationResult =
        clazz.annotations.find { annotation -> annotation is HttpRestResource }?.let { HttpRpcValidationResult() }
            ?: RestValidationResult(listOf(error))
}