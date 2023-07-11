package net.corda.rest.tools.annotations.validation

import net.corda.rest.RestResource
import net.corda.rest.annotations.RestApiVersion
import net.corda.rest.annotations.retrieveApiVersionsSet
import net.corda.rest.tools.annotations.validation.utils.EndpointType
import net.corda.rest.tools.annotations.validation.utils.endpointPath
import net.corda.rest.tools.annotations.validation.utils.endpointType
import net.corda.rest.tools.annotations.validation.utils.endpoints
import net.corda.rest.tools.annotations.validation.utils.restApiVersions
import java.lang.reflect.Method

/**
 * Validates that two or more endpoints in the same class do not have the same name,
 * as it would create a path name clash in the server.
 */
internal class EndpointNameConflictValidator(private val clazz: Class<out RestResource>) : RestValidator {

    companion object {
        fun error(path: String?, type: EndpointType, method: Method): String =
            "Duplicate endpoint path '$path' with $type HTTP method for version range (${method.restApiVersions.minVersion} -> " +
                    "${method.restApiVersions.maxVersion}) in '${method.declaringClass.simpleName}.${method.name}'."
    }

    override fun validate(): RestValidationResult = validateSameTypeEndpoints(clazz.endpoints)

    private fun validateSameTypeEndpoints(endpoints: List<Method>): RestValidationResult {
        val pathsToTypesAndVersions = mutableSetOf<Triple<String?, EndpointType, RestApiVersion>>()
        return endpoints.fold(RestValidationResult()) { total, method ->
            val endpointType = method.endpointType
            total + pathsToTypesAndVersions.validateNoDuplicatePath(method, endpointType)
        }
    }

    private fun MutableSet<Triple<String?, EndpointType, RestApiVersion>>.validateNoDuplicatePath(
        method: Method,
        type: EndpointType
    ): RestValidationResult {
        val path = method.endpointPath(type)?.lowercase()
        val newVersions = retrieveApiVersionsSet(method.restApiVersions.minVersion, method.restApiVersions.maxVersion)

        newVersions.forEach {
            val version = Triple(path, type, it)
            if (!this.add(version)) {
                return RestValidationResult(listOf(error(path, type, method)))
            }
        }

        return RestValidationResult()
    }
}
