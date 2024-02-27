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
        fun error(path: String?, type: EndpointType, method: Method, conflictingMethod: Method): String =
            "Duplicate endpoint path '$path' with $type HTTP method in '${method.declaringClass.simpleName}.${method.name}' " +
                "for version range (${method.restApiVersions.minVersion} -> ${method.restApiVersions.maxVersion}). " +
                "Conflicting method: '${conflictingMethod.declaringClass.simpleName}.${conflictingMethod.name}' " +
                "with versions (${conflictingMethod.restApiVersions.minVersion} -> ${conflictingMethod.restApiVersions.maxVersion})."
    }

    override fun validate(): RestValidationResult = validateSameTypeEndpoints(clazz.endpoints)

    private fun validateSameTypeEndpoints(endpoints: List<Method>): RestValidationResult {
        val pathsToTypesAndVersions = mutableMapOf<Triple<String?, EndpointType, RestApiVersion>, Method>()
        return endpoints.fold(RestValidationResult()) { total, method ->
            val endpointType = method.endpointType
            total + pathsToTypesAndVersions.validateNoDuplicatePath(method, endpointType)
        }
    }

    private fun MutableMap<Triple<String?, EndpointType, RestApiVersion>, Method>.validateNoDuplicatePath(
        method: Method,
        type: EndpointType
    ): RestValidationResult {
        val path = method.endpointPath(type)?.lowercase()
        val newVersions = retrieveApiVersionsSet(method.restApiVersions.minVersion, method.restApiVersions.maxVersion)

        val conflicts = mutableSetOf<String>()
        newVersions.forEach {
            val version = Triple(path, type, it)
            if (this.keys.contains(version)) {
                val existingMethod = this.getValue(version)
                conflicts.add(error(path, type, method, existingMethod))
            } else {
                this[version] = method
            }
        }

        return RestValidationResult(conflicts.toList())
    }
}
