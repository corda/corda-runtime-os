package net.corda.rest.tools.annotations.validation

import net.corda.rest.RestResource
import net.corda.rest.annotations.RestApiVersion
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

    private data class VersionRange(val minVersion: RestApiVersion, val maxVersion: RestApiVersion) {
        fun overlapsWith(other: VersionRange): Boolean {
            return other.minVersion <= maxVersion && other.maxVersion >= minVersion
        }
    }

    private fun validateSameTypeEndpoints(endpoints: List<Method>): RestValidationResult {
        val pathsAndTypesToVersions = mutableMapOf<Pair<String?, EndpointType>, MutableList<VersionRange>>()
        return endpoints.fold(RestValidationResult()) { total, method ->
            val endpointType = method.endpointType
            total + pathsAndTypesToVersions.validateNoDuplicatePath(method, endpointType)
        }
    }

    private fun MutableMap<Pair<String?, EndpointType>, MutableList<VersionRange>>.validateNoDuplicatePath(
        method: Method,
        type: EndpointType
    ): RestValidationResult {
        val path = method.endpointPath(type)?.lowercase()
        val newVersionRange = VersionRange(method.restApiVersions.minVersion, method.restApiVersions.maxVersion)
        return if (this[path to type]?.any { it.overlapsWith(newVersionRange) } == true) {
            RestValidationResult(listOf(error(path, type, method)))
        } else {
            this.getOrPut(path to type) { mutableListOf() }.add(newVersionRange)
            RestValidationResult()
        }
    }
}
