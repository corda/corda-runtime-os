package net.corda.httprpc.tools.annotations.validation

import net.corda.httprpc.RestResource
import net.corda.httprpc.annotations.HttpRestResource
import net.corda.httprpc.tools.annotations.extensions.path

/**
 * Validates that multiple classes do not have the same resource path.
 */
internal class ResourceNameConflictValidator(private val classes: List<Class<out RestResource>>) : HttpRpcValidator {
    override fun validate(): HttpRpcValidationResult {
        val resourceNames = mutableSetOf<String>()
        return classes.filter {
            it.annotations.any { annotation -> annotation is HttpRestResource }
        }.map {
            it.getAnnotation(HttpRestResource::class.java).path(it).lowercase()
        }.fold(HttpRpcValidationResult()) { total, next ->
            total + if (next in resourceNames) {
                HttpRpcValidationResult(listOf("Duplicate resource name: $next"))
            } else {
                resourceNames.add(next)
                HttpRpcValidationResult()
            }
        }
    }
}
