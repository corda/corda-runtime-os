package net.corda.rest.tools.annotations.validation

import net.corda.rest.RestResource
import net.corda.rest.annotations.HttpRestResource
import net.corda.rest.annotations.RestApiVersion
import net.corda.rest.annotations.retrieveApiVersionsSet
import net.corda.rest.tools.annotations.extensions.path

/**
 * Validates that multiple classes do not have the same resource path.
 */
internal class ResourceNameConflictValidator(private val classes: List<Class<out RestResource>>) : RestValidator {
    companion object {
        fun error(
            path: String?,
            clazz: Class<out RestResource>,
            conflictingClass: Class<out RestResource>
        ): String {
            val resource = clazz.getAnnotation(HttpRestResource::class.java)
            val conflictingResource = conflictingClass.getAnnotation(HttpRestResource::class.java)
            return "Duplicate resource with path '$path' in '${clazz.simpleName}' " +
                "for version range (${resource.minVersion} -> ${resource.maxVersion}). " +
                "Conflicting resource: '${conflictingClass.simpleName}' " +
                "with versions (${conflictingResource.minVersion} -> ${conflictingResource.maxVersion})."
        }
    }
    override fun validate(): RestValidationResult {
        val pathsToTypesAndVersions = mutableMapOf<Pair<String?, RestApiVersion>, Class<out RestResource>>()
        return classes.filter {
            it.annotations.any { annotation -> annotation is HttpRestResource }
        }.fold(RestValidationResult()) { total, next ->
            total + total + pathsToTypesAndVersions.validateNoDuplicatePath(next)
        }
    }

    private fun MutableMap<Pair<String?, RestApiVersion>, Class<out RestResource>>.validateNoDuplicatePath(
        resourceClass: Class<out RestResource>,
    ): RestValidationResult {
        val resource = resourceClass.getAnnotation(HttpRestResource::class.java)
        val path = resource.path(resourceClass).lowercase()
        val newVersions = retrieveApiVersionsSet(resource.minVersion, resource.maxVersion)

        val conflicts = mutableSetOf<String>()
        newVersions.forEach {
            val version = Pair(path, it)
            if (this.keys.contains(version)) {
                val existingClass = this.getValue(version)
                conflicts.add(error(path, resourceClass, existingClass))
            } else {
                this[version] = resourceClass
            }
        }

        return RestValidationResult(conflicts.toList())
    }
}
