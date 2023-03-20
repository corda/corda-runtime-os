package net.corda.rest.tools.annotations.validation

import net.corda.rest.RestResource
import net.corda.rest.annotations.isRestParameterAnnotation
import net.corda.rest.tools.annotations.validation.utils.endpoints
import java.lang.reflect.Method

/**
 * Validates that every method parameter is annotated with up to one of the expected annotations.
 */
internal class ParameterAnnotationValidator(private val clazz: Class<out RestResource>) : RestValidator {
    override fun validate(): RestValidationResult =
        clazz.endpoints.fold(RestValidationResult()) { total, method ->
            total + validateParametersOf(method)
        }

    private fun validateParametersOf(method: Method) =
        method.parameters.fold(RestValidationResult()) { total, parameter ->
            total + try {
                parameter.annotations.single {
                    it.isRestParameterAnnotation()
                }
                RestValidationResult()
            } catch (e: IllegalArgumentException) {
                RestValidationResult(listOf("Parameter ${method.name}.${parameter.name} can't have multiple types"))
            } catch (e: NoSuchElementException) {
                RestValidationResult()
            }
        }
}