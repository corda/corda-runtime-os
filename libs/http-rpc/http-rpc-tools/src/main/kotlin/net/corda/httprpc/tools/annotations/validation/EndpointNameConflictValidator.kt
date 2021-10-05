package net.corda.httprpc.tools.annotations.validation

import net.corda.httprpc.RpcOps
import net.corda.httprpc.tools.annotations.validation.utils.EndpointType
import net.corda.httprpc.tools.annotations.validation.utils.endpointPath
import net.corda.httprpc.tools.annotations.validation.utils.endpointType
import net.corda.httprpc.tools.annotations.validation.utils.endpoints
import java.lang.reflect.Method

/**
 * Validates that two or more endpoints in the same class do not have the same name,
 * as it would create a path name clash in the server.
 */
internal class EndpointNameConflictValidator(private val clazz: Class<out RpcOps>) : HttpRpcValidator {

    companion object {
        fun error(path: String, type: EndpointType) = "Duplicate endpoint path '$path' for $type method."
    }

    override fun validate(): HttpRpcValidationResult = validateSameTypeEndpoints(clazz.endpoints)

    private fun validateSameTypeEndpoints(endpoints: List<Method>): HttpRpcValidationResult {
        val pathsToTypes = mutableSetOf<Pair<String, EndpointType>>()
        return endpoints.fold(HttpRpcValidationResult()) { total, method ->
            val endpointType = method.endpointType
            total + pathsToTypes.validateNoDuplicatePath(method, endpointType)
        }
    }

    private fun MutableSet<Pair<String, EndpointType>>.validateNoDuplicatePath(
        method: Method,
        type: EndpointType
    ): HttpRpcValidationResult {
        val path = method.endpointPath(type).toLowerCase()
        return if (this.contains(path to type)) {
            HttpRpcValidationResult(listOf(error(path, type)))
        } else {
            this.add(path to type)
            HttpRpcValidationResult()
        }
    }
}
