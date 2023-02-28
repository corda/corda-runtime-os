package net.corda.rest.tools.annotations.validation

import net.corda.rest.RestResource
import net.corda.rest.annotations.HttpRestResource

/**
 * Validates that every class validated is annotated with [HttpRestResource]
 */
internal class ResourceAnnotationValidator(private val clazz: Class<out RestResource>) : RestValidator {

    companion object {
        val error = "RestResource interface must be annotated with ${HttpRestResource::class.qualifiedName}."
    }

    override fun validate(): RestValidationResult =
        clazz.annotations.find { annotation -> annotation is HttpRestResource }?.let { RestValidationResult() }
            ?: RestValidationResult(listOf(error))
}