package net.corda.httprpc.tools.annotations.validation

import net.corda.httprpc.RestResource
import net.corda.httprpc.annotations.isHttpRpcParameterAnnotation
import net.corda.httprpc.tools.annotations.validation.utils.endpoints
import java.lang.reflect.Method

/**
 * Validates that every method parameter is annotated with up to one of the expected annotations.
 */
internal class ParameterAnnotationValidator(private val clazz: Class<out RestResource>) : HttpRpcValidator {
    override fun validate(): HttpRpcValidationResult =
        clazz.endpoints.fold(HttpRpcValidationResult()) { total, method ->
            total + validateParametersOf(method)
        }

    private fun validateParametersOf(method: Method) =
        method.parameters.fold(HttpRpcValidationResult()) { total, parameter ->
            total + try {
                parameter.annotations.single {
                    it.isHttpRpcParameterAnnotation()
                }
                HttpRpcValidationResult()
            } catch (e: IllegalArgumentException) {
                HttpRpcValidationResult(listOf("Parameter ${method.name}.${parameter.name} can't have multiple types"))
            } catch (e: NoSuchElementException) {
                HttpRpcValidationResult()
            }
        }
}