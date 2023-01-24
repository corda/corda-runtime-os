package net.corda.httprpc.tools.annotations.validation

import net.corda.httprpc.RestResource
import net.corda.httprpc.annotations.HttpRpcResource
import net.corda.httprpc.tools.annotations.extensions.path

/**
 * Validates that multiple classes do not have the same resource path.
 */
internal class ResourceNameConflictValidator(private val classes: List<Class<out RestResource>>) : RestValidator {
    override fun validate(): RestValidationResult {
        val resourceNames = mutableSetOf<String>()
        return classes.filter {
            it.annotations.any { annotation -> annotation is HttpRpcResource }
        }.map {
            it.getAnnotation(HttpRpcResource::class.java).path(it).lowercase()
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
