package net.corda.rest.tools.annotations.validation

import net.corda.rest.RestResource
import net.corda.rest.tools.annotations.validation.utils.endpoints
import net.corda.rest.tools.annotations.validation.utils.isEqualOrChildOf
import net.corda.rest.tools.annotations.validation.utils.restApiVersions
import java.lang.reflect.Method

/**
 * Validates that for every method in the class `minVersion` is less than `maxVersion` and also that the two versions
 * are related
 */
internal class EndpointMinMaxVersionValidator(private val clazz: Class<out RestResource>) : RestValidator {

    companion object {
        fun error(method: Method): String =
            "Invalid combination of min/max version for endpoint " +
                "HTTP method in '${method.declaringClass.simpleName}.${method.name}'."
    }

    override fun validate(): RestValidationResult = validateVersions(clazz.endpoints)

    private fun validateVersions(endpoints: List<Method>): RestValidationResult {
        return endpoints.fold(RestValidationResult()) { total, method ->
            method.restApiVersions.let {
                if (it.maxVersion.isEqualOrChildOf(it.minVersion)) {
                    total
                } else {
                    total + RestValidationResult(listOf(error(method)))
                }
            }
        }
    }
}
