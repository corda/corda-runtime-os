package net.corda.flow.application.services.impl.interop.facade

import net.corda.v5.application.interop.facade.FacadeId
import net.corda.v5.application.interop.facade.FacadeMethod
import net.corda.v5.application.interop.facade.FacadeMethodType
import net.corda.v5.application.interop.facade.FacadeRequest
import net.corda.v5.application.interop.facade.FacadeResponse
import net.corda.v5.application.interop.parameters.TypedParameter
import net.corda.v5.application.interop.parameters.TypedParameterValue

/**
 * A [FacadeMethod] is a method of a [Facade].
 * @param facadeId The [FacadeId] of the owning facade.
 * @param name The name of the method
 * @param inParameters the input parameters of the method.
 * @param outParameters the output parameters of the method.
 */
data class FacadeMethodImpl(
    private val facadeId: FacadeId,
    private val name: String,
    private val type: FacadeMethodType,
    private val inParameters: List<TypedParameter<*>>,
    private val outParameters: List<TypedParameter<*>>
) : FacadeMethod {

    /**
     * The qualified name of the method, which is the name of the facade followed by the name of the method.
     */
    private val qualifiedName: String = "$facadeId/$name"

    private val inParameterMap = inParameters.associateBy { it.name }
    private val outParameterMap = outParameters.associateBy { it.name }

    /**
     * Obtain the in parameter with the given name.
     * @param parameterName The name of the parameter to obtain.
     * @param expectedType The expected type of the parameter.
     */
    @Suppress("UNCHECKED_CAST")
    override fun <T : Any> inParameter(parameterName: String, expectedType: Class<T>): TypedParameter<T> {
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
     * @param parameterName: The name of the parameter to obtain.
     */
    override fun untypedInParameter(parameterName: String): TypedParameter<*>? {
        return inParameterMap[parameterName]
    }

    /**
     * Obtain the out parameter with the given name.
     * @param parameterName The name of the parameter to obtain.
     * @param expectedType The expected type of the parameter.
     */
    @Suppress("UNCHECKED_CAST")
    override fun <T : Any> outParameter(parameterName: String, expectedType: Class<T>): TypedParameter<T> {
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
     * @param parameterValues The parameter values to pass to the method.
     */
    override fun request(vararg parameterValues: TypedParameterValue<*>): FacadeRequest {
        return FacadeRequestImpl(facadeId, name, parameterValues.toList())
    }

    /**
     * Create a [FacadeResponse] for this method.
     * @param parameterValues The parameter values to return from the method.
     */
    override fun response(vararg parameterValues: TypedParameterValue<*>): FacadeResponse {
        return FacadeResponseImpl(facadeId, name, parameterValues.toList())
    }

    override fun getFacadeId(): FacadeId {
        return facadeId
    }

    override fun getName(): String {
        return name
    }

    override fun getType(): FacadeMethodType {
        return type
    }

    override fun getInParameters(): List<TypedParameter<*>> {
        return inParameters
    }

    override fun getOutParameters(): List<TypedParameter<*>> {
        return outParameters
    }

    override fun getQualifiedName(): String {
        return qualifiedName
    }

}