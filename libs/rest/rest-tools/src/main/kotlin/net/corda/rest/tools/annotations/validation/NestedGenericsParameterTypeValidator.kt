package net.corda.rest.tools.annotations.validation

import net.corda.rest.RestResource
import net.corda.rest.annotations.isRestEndpointAnnotation
import java.lang.reflect.Method
import java.lang.reflect.Parameter
import java.lang.reflect.ParameterizedType

class NestedGenericsParameterTypeValidator(private val clazz: Class<out RestResource>) : RestValidator {

    companion object {
        fun error(method: Method) = "Method \"${method.name}\" has nested generic parameter types. " +
                "This complex structure is currently not supported. Please consider simplifying it."
    }

    override fun validate(): RestValidationResult =
        clazz.methods.fold(RestValidationResult()) { total, method ->
            total + if (method.annotations.any { it.isRestEndpointAnnotation() }) {
                validateTypeNotNestedGenerics(method)
            } else RestValidationResult()
        }

    private fun validateTypeNotNestedGenerics(method: Method) =
        if (method.parameters.any { it.isNestedGenericType() }) {
            RestValidationResult(listOf(error(method)))
        } else RestValidationResult()

    private fun Parameter.isNestedGenericType(): Boolean = (this.parameterizedType is ParameterizedType
            && (this.parameterizedType as ParameterizedType).actualTypeArguments.any { nested -> nested !is Class<*> })
}
