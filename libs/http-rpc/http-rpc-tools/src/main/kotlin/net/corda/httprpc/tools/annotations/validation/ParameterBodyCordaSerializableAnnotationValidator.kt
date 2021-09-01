package net.corda.httprpc.tools.annotations.validation

import net.corda.httprpc.tools.annotations.validation.utils.isBodyParameter
import net.corda.v5.application.messaging.RPCOps
import net.corda.v5.base.annotations.CordaSerializable
import net.corda.v5.httprpc.api.annotations.HttpRpcGET
import net.corda.v5.httprpc.api.annotations.HttpRpcPOST
import java.lang.reflect.Method
import java.lang.reflect.Parameter

/**
 * Validates that every body method parameter is [CordaSerializable].
 */
class ParameterBodyCordaSerializableAnnotationValidator(private val clazz: Class<out RPCOps>) : HttpRpcValidator {

    companion object {

        fun error(method: Method, parameterName: String) =
            "Body parameter ${method.name}.${parameterName} needs to be a primitive type, primitive type wrapper or a " +
                    "class explicitly annotated as CordaSerializable."

        private val wrappers = listOf(
            Int::class.java, Integer::class.java,
            Long::class.java, Long::class.javaObjectType,
            Boolean::class.java, Boolean::class.javaObjectType,
            String::class.java,
            Double::class.javaObjectType, Double::class.java,
            Byte::class.java, Byte::class.javaObjectType,
            Float::class.java, Float::class.javaObjectType,
            Short::class.java, Short::class.javaObjectType,
            Char::class.java, Char::class.javaObjectType
        )
    }

    override fun validate(): HttpRpcValidationResult =
        clazz.methods.fold(HttpRpcValidationResult()) { total, method ->
            total + if (method.annotations.any { it is HttpRpcPOST || it is HttpRpcGET }) {
                validateBody(method)
            } else HttpRpcValidationResult()
        }

    private fun validateBody(method: Method) =
        method.parameters.filter { it.isBodyParameter() }
            .fold(HttpRpcValidationResult()) { total, nextParameter ->
                total + if (isSerializableBody(nextParameter)) HttpRpcValidationResult()
                else HttpRpcValidationResult(listOf(error(method, nextParameter.name)))
            }

    private fun isSerializableBody(parameter: Parameter): Boolean {
        return parameter.type.annotations.any { it is CordaSerializable }
                || parameter.type.isPrimitive
                || parameter.type in wrappers
    }
}
