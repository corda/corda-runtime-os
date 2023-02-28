package net.corda.rest.tools.annotations.validation

import net.corda.rest.RestResource
import net.corda.rest.annotations.HttpRestResource
import net.corda.rest.tools.annotations.extensions.path

/**
 * Validates that multiple classes do not have the same resource path.
 */
internal class ResourceNameConflictValidator(private val classes: List<Class<out RestResource>>) : RestValidator {
    override fun validate(): RestValidationResult {
        val resourceNames = mutableSetOf<String>()
        return classes.filter {
            it.annotations.any { annotation -> annotation is HttpRestResource }
        }.map {
            it.getAnnotation(HttpRestResource::class.java).path(it).lowercase()
        }.fold(RestValidationResult()) { total, next ->
            total + if (next in resourceNames) {
                RestValidationResult(listOf("Duplicate resource name: $next"))
            } else {
                resourceNames.add(next)
                RestValidationResult()
            }
        }
    }
}
