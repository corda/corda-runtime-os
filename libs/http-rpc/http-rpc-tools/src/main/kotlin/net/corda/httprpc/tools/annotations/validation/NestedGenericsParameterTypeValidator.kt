package net.corda.httprpc.tools.annotations.validation

import net.corda.httprpc.RestResource
import net.corda.httprpc.annotations.isRestEndpointAnnotation
import java.lang.reflect.Method
import java.lang.reflect.Parameter
import java.lang.reflect.ParameterizedType

class NestedGenericsParameterTypeValidator(private val clazz: Class<out RestResource>) : HttpRpcValidator {

    companion object {
        fun error(method: Method) = "Method \"${method.name}\" has nested generic parameter types. " +
                "This complex structure is currently not supported. Please consider simplifying it."
    }

    override fun validate(): HttpRpcValidationResult =
        clazz.methods.fold(HttpRpcValidationResult()) { total, method ->
            total + if (method.annotations.any { it.isRestEndpointAnnotation() }) {
                validateTypeNotNestedGenerics(method)
            } else HttpRpcValidationResult()
        }

    private fun validateTypeNotNestedGenerics(method: Method) =
        if (method.parameters.any { it.isNestedGenericType() }) {
            HttpRpcValidationResult(listOf(error(method)))
        } else HttpRpcValidationResult()

    private fun Parameter.isNestedGenericType(): Boolean = (this.parameterizedType is ParameterizedType
            && (this.parameterizedType as ParameterizedType).actualTypeArguments.any { nested -> nested !is Class<*> })
}
