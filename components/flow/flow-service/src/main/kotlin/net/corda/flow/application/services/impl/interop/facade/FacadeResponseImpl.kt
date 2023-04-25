package net.corda.flow.application.services.impl.interop.facade

import com.fasterxml.jackson.databind.annotation.JsonDeserialize
import com.fasterxml.jackson.databind.annotation.JsonSerialize
import net.corda.v5.application.interop.facade.FacadeId
import net.corda.v5.application.interop.facade.FacadeResponse
import net.corda.v5.application.interop.parameters.ParameterType

/**
 * A [FacadeResponseImpl] is a response to a [FacadeRequest] to invoke a [FacadeMethod] on a [Facade].
 *
 * @param method The method that was invoked.
 * @param outParameters The values of the out parameters of the method.
 */
@JsonSerialize(using = FacadeResponseSerializer::class)
@JsonDeserialize(using = FacadeResponseDeserializer::class)
data class FacadeResponseImpl(
    val facadeId: FacadeId,
    val methodName: String,
    val outParameters: List<ParameterType<*>>
) : FacadeResponse {

    private val outParametersByName = outParameters.associateBy { it.typeName }
    override fun getFacadeId(): FacadeId {
        return facadeId
    }

    override fun getMethodName(): String {
        return methodName
    }

    override fun getOutParameters(): List<ParameterType<*>> {
        return outParameters
    }

    /**
     * Get the value of an out parameter.
     *
     * @param parameter The parameter to get the value of.
     */
    @Suppress("UNCHECKED_CAST")
    override operator fun <T : Any> get(parameter: ParameterType<T>): T {
        val value = outParametersByName[parameter.typeLabel.name]
            ?: throw IllegalArgumentException("No value for parameter ${parameter.typeLabel}")

        return (value as? T)
            ?: throw IllegalArgumentException("Value for parameter ${parameter.typeLabel} is of the wrong type")
    }
}