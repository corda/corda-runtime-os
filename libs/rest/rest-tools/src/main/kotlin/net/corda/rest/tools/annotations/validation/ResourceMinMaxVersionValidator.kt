package net.corda.rest.tools.annotations.validation

import net.corda.rest.RestResource
import net.corda.rest.annotations.HttpRestResource
import net.corda.rest.tools.annotations.validation.utils.isEqualOrChildOf
import net.corda.rest.tools.annotations.validation.utils.versions

/**
 * Validates that there are no violations to `minVersion` and `maxVersion` on `RestResource` level
 */
internal class ResourceMinMaxVersionValidator(private val classes: List<Class<out RestResource>>) : RestValidator {

    companion object {
        fun error(clazz: Class<out RestResource>): String =
            "Invalid combination of min/max version for resource '${clazz.simpleName}'."
    }

    override fun validate(): RestValidationResult {
        return classes.filter {
            it.annotations.any { annotation -> annotation is HttpRestResource }
        }.fold(RestValidationResult()) { total, clazz ->
            val versions = clazz.getAnnotation(HttpRestResource::class.java).versions
            if (versions.maxVersion.isEqualOrChildOf(versions.minVersion)) {
                total
            } else {
                total + RestValidationResult(listOf(error(clazz)))
            }
        }
    }
}
