package net.corda.httprpc.tools.annotations.validation

import net.corda.httprpc.tools.annotations.extensions.path
import net.corda.v5.application.messaging.RPCOps
import net.corda.v5.httprpc.api.annotations.HttpRpcResource

/**
 * Validates that multiple classes do not have the same resource path.
 */
internal class ResourceNameConflictValidator(private val classes: List<Class<out RPCOps>>) : HttpRpcValidator {
    override fun validate(): HttpRpcValidationResult {
        val resourceNames = mutableSetOf<String>()
        return classes.filter {
            it.annotations.any { annotation -> annotation is HttpRpcResource }
        }.map {
            it.getAnnotation(HttpRpcResource::class.java).path(it).toLowerCase()
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