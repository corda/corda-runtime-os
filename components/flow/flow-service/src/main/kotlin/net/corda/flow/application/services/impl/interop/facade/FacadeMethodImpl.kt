package net.corda.flow.application.services.impl.interop.facade

import net.corda.v5.application.interop.facade.FacadeId
import net.corda.v5.application.interop.facade.FacadeMethod
import net.corda.v5.application.interop.facade.FacadeMethodType
import net.corda.v5.application.interop.facade.FacadeRequest
import net.corda.v5.application.interop.facade.FacadeResponse
import net.corda.v5.application.interop.parameters.ParameterType

/**
 * A [FacadeMethod] is a method of a [Facade].
 *
 * @param facadeId The [FacadeId] of the owning facade.
 * @param name The name of the method
 * @param inParameters the input parameters of the method.
 * @param outParameters the output parameters of the method.
 */
data class FacadeMethodImpl(
    val facadeId: FacadeId,
    val name: String,
    val type: FacadeMethodType,
    val inParameters: List<ParameterType<*>>,
    val outParameters: List<ParameterType<*>>
) : FacadeMethod {

    /**
     * The qualified name of the method, which is the name of the facade followed by the name of the method.
     */
    val qualifiedName: String get() = "$facadeId/$name"

    private val inParameterMap = inParameters.associateBy { it.typeLabel.typeName }
    private val outParameterMap = outParameters.associateBy { it.typeLabel.typeName }

    /**
     * Get the input parameter with the given name.
     *
     * @param parameterName The name of the input parameter.
     */
    override inline fun <reified T : Any> inParameter(parameterName: String): ParameterType<T> {
        return inParameter(parameterName, T::class.java)
    }

    /**
     * Obtain the in parameter with the given name.
     *
     * @param parameterName The name of the parameter to obtain.
     * @param expectedType The expected type of the parameter.
     */
    @Suppress("UNCHECKED_CAST")
    override fun <T : Any> inParameter(parameterName: String, expectedType: Class<T>): ParameterType<T> {
        val untyped = untypedInParameter(parameterName)
            ?: throw IllegalArgumentException("No such input parameter: $parameterName")

        if (untyped.expectedType != expectedType) {
            throw IllegalArgumentException(
                "Parameter $parameterName is of type ${untyped.expectedType}, " +
                        "not $expectedType"
            )
        }

        return untyped as ParameterType<T>
    }


    /**
     * Get the in parameter with the given name, without checking that its type matches an expected type.
     *
     * @param parameterName: The name of the parameter to obtain.
     */
    override fun untypedInParameter(parameterName: String): ParameterType<*>? {
        return inParameterMap[parameterName]
    }

    /**
     * Get the output parameter with the given name.
     *
     * @param parameterName The name of the output parameter.
     */
    override inline fun <reified T : Any> outParameter(parameterName: String): ParameterType<T> {
        return outParameter(parameterName, T::class.java)
    }

    /**
     * Obtain the out parameter with the given name.
     *
     * @param parameterName The name of the parameter to obtain.
     * @param expectedType The expected type of the parameter.
     */
    @Suppress("UNCHECKED_CAST")
    override fun <T : Any> outParameter(parameterName: String, expectedType: Class<T>): ParameterType<T> {
        val untyped =
            outParameterMap[parameterName] ?: throw IllegalArgumentException("No such output parameter: $parameterName")

        if (untyped.expectedType != expectedType) {
            throw IllegalArgumentException(
                "Parameter $parameterName is of type ${untyped.expectedType}, " +
                        "not $expectedType"
            )
        }

        return untyped as ParameterType<T>
    }

    /**
     * Create a [FacadeRequest] for this method.
     *
     * @param parameterValues The parameter values to pass to the method.
     */
    override fun request(vararg parameterValues: ParameterType<*>): FacadeRequest {
        return FacadeRequestImpl(facadeId, name, parameterValues.toList())
    }

    /**
     * Create a [FacadeResponse] for this method.
     *
     * @param parameterValues The parameter values to return from the method.
     */
    override fun response(vararg parameterValues: ParameterType<*>): FacadeResponse {
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

    override fun getInParameters(): List<ParameterType<*>> {
        return inParameters
    }

    override fun getOutParameters(): List<ParameterType<*>> {
        return outParameters
    }

    override fun getQualifiedName(): String {
        return qualifiedName
    }

}