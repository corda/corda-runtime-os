package org.corda.weft.facade

import org.corda.weft.parameters.TypedParameter
import org.corda.weft.parameters.TypedParameterValue

/**
 * A [FacadeMethod] is a method of a [Facade].
 *
 * @param facadeId The [FacadeId3] of the owning facade.
 * @param name The name of the method
 * @param inParameters the input parameters of the method.
 * @param outParameters the output parameters of the method.
 */
data class FacadeMethod(
    val facadeId: FacadeId3,
    val name: String,
    val type: FacadeMethodType,
    val inParameters: List<TypedParameter<*>>,
    val outParameters: List<TypedParameter<*>>
) {

    /**
     * The qualified name of the method, which is the name of the facade followed by the name of the method.
     */
    val qualifiedName: String get() = "$facadeId/$name"

    private val inParameterMap = inParameters.associateBy { it.name }
    private val outParameterMap = outParameters.associateBy { it.name }

    /**
     * Get the input parameter with the given name.
     *
     * @param parameterName The name of the input parameter.
     */
    inline fun <reified T : Any> inParameter(parameterName: String): TypedParameter<T> {
        return inParameter(parameterName, T::class.java)
    }

    /**
     * Obtain the in parameter with the given name.
     *
     * @param parameterName The name of the parameter to obtain.
     * @param expectedType The expected type of the parameter.
     */
    @Suppress("UNCHECKED_CAST")
    fun <T : Any> inParameter(parameterName: String, expectedType: Class<T>): TypedParameter<T> {
        val untyped = untypedInParameter(parameterName)
            ?: throw IllegalArgumentException("No such input parameter: $parameterName")

        if (untyped.type.expectedType != expectedType) {
            throw IllegalArgumentException(
                "Parameter $parameterName is of type ${untyped.type.expectedType}, " +
                        "not $expectedType"
            )
        }

        return untyped as TypedParameter<T>
    }


    /**
     * Get the in parameter with the given name, without checking that its type matches an expected type.
     *
     * @param parameterName: The name of the parameter to obtain.
     */
    fun untypedInParameter(parameterName: String): TypedParameter<*>? {
        return inParameterMap[parameterName]
    }

    /**
     * Get the output parameter with the given name.
     *
     * @param parameterName The name of the output parameter.
     */
    inline fun <reified T : Any> outParameter(parameterName: String): TypedParameter<T> {
        return outParameter(parameterName, T::class.java)
    }

    /**
     * Obtain the out parameter with the given name.
     *
     * @param parameterName The name of the parameter to obtain.
     * @param expectedType The expected type of the parameter.
     */
    @Suppress("UNCHECKED_CAST")
    fun <T : Any> outParameter(parameterName: String, expectedType: Class<T>): TypedParameter<T> {
        val untyped =
            outParameterMap[parameterName] ?: throw IllegalArgumentException("No such output parameter: $parameterName")

        if (untyped.type.expectedType != expectedType) {
            throw IllegalArgumentException(
                "Parameter $parameterName is of type ${untyped.type.expectedType}, " +
                        "not $expectedType"
            )
        }

        return untyped as TypedParameter<T>
    }

    /**
     * Create a [FacadeRequest] for this method.
     *
     * @param parameterValues The parameter values to pass to the method.
     */
    fun request(vararg parameterValues: TypedParameterValue<*>): FacadeRequest {
        return FacadeRequest(facadeId, name, parameterValues.toList())
    }

    /**
     * Create a [FacadeResponse] for this method.
     *
     * @param parameterValues The parameter values to return from the method.
     */
    fun response(vararg parameterValues: TypedParameterValue<*>): FacadeResponse {
        return FacadeResponse(facadeId, name, parameterValues.toList())
    }

}

enum class FacadeMethodType {
    COMMAND,
    QUERY
}
