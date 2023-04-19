package org.corda.weft.facade

import com.fasterxml.jackson.databind.annotation.JsonDeserialize
import com.fasterxml.jackson.databind.annotation.JsonSerialize
import org.corda.weft.parameters.TypedParameter
import org.corda.weft.parameters.TypedParameterValue

/**
 * A [FacadeResponse] is a response to a [FacadeRequest] to invoke a [FacadeMethod] on a [Facade].
 *
 * @param method The method that was invoked.
 * @param outParameters The values of the out parameters of the method.
 */
@JsonSerialize(using = FacadeResponseSerializer::class)
@JsonDeserialize(using = FacadeResponseDeserializer::class)
data class FacadeResponse(
    val facadeId: FacadeId,
    val methodName: String,
    val outParameters: List<TypedParameterValue<*>>
) {

    private val outParametersByName = outParameters.associateBy { it.parameter.name }

    /**
     * Get the value of an out parameter.
     *
     * @param parameter The parameter to get the value of.
     */
    @Suppress("UNCHECKED_CAST")
    operator fun <T : Any> get(parameter: TypedParameter<T>): T {
        val value = outParametersByName[parameter.name]
            ?: throw IllegalArgumentException("No value for parameter ${parameter.name}")

        return (value as? TypedParameterValue<T>)?.value
            ?: throw IllegalArgumentException("Value for parameter ${parameter.name} is of the wrong type")
    }
}