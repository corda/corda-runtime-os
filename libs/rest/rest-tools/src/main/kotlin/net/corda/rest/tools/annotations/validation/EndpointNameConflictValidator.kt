package net.corda.rest.tools.annotations.validation

import net.corda.rest.RestResource
import net.corda.rest.tools.annotations.validation.utils.EndpointType
import net.corda.rest.tools.annotations.validation.utils.endpointPath
import net.corda.rest.tools.annotations.validation.utils.endpointType
import net.corda.rest.tools.annotations.validation.utils.endpoints
import java.lang.reflect.Method

/**
 * Validates that two or more endpoints in the same class do not have the same name,
 * as it would create a path name clash in the server.
 */
internal class EndpointNameConflictValidator(private val clazz: Class<out RestResource>) : RestValidator {

    companion object {
        fun error(path: String?, type: EndpointType, method: Method): String =
            "Duplicate endpoint path '$path' for $type HTTP method in '${method.declaringClass.simpleName}.${method.name}'."
    }

    override fun validate(): RestValidationResult = validateSameTypeEndpoints(clazz.endpoints)

    private fun validateSameTypeEndpoints(endpoints: List<Method>): RestValidationResult {
        val pathsToTypes = mutableSetOf<Pair<String?, EndpointType>>()
        return endpoints.fold(RestValidationResult()) { total, method ->
            val endpointType = method.endpointType
            total + pathsToTypes.validateNoDuplicatePath(method, endpointType)
        }
    }

    private fun MutableSet<Pair<String?, EndpointType>>.validateNoDuplicatePath(
        method: Method,
        type: EndpointType
    ): RestValidationResult {
        val path = method.endpointPath(type)?.lowercase()
        return if (this.contains(path to type)) {
            RestValidationResult(listOf(error(path, type, method)))
        } else {
            this.add(path to type)
            RestValidationResult()
        }
    }
}
