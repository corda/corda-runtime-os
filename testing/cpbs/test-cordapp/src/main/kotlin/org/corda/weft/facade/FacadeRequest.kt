package org.corda.weft.facade

import com.fasterxml.jackson.databind.annotation.JsonDeserialize
import com.fasterxml.jackson.databind.annotation.JsonSerialize
import org.corda.weft.parameters.TypedParameter
import org.corda.weft.parameters.TypedParameterValue

/**
 * A [FacadeRequest] is a request to invoke a [FacadeMethod] on a [Facade].
 *
 * @param facadeId The id of the facade to which the method belongs.
 * @param methodName The name of the method to invoke.
 * @param inParameters The parameter values to pass to the method.
 */
@JsonSerialize(using = FacadeRequestSerializer::class)
@JsonDeserialize(using = FacadeRequestDeserializer::class)
data class FacadeRequest(
    val facadeId: FacadeId3,
    val methodName: String,
    val inParameters: List<TypedParameterValue<*>>
) {

    private val inParametersByName = inParameters.associateBy { it.parameter.name }

    /**
     * Get the value of a parameter by name.
     *
     * @param parameter The parameter to get the value of.
     */
    @Suppress("UNCHECKED_CAST")
    operator fun <T : Any> get(parameter: TypedParameter<T>): T {
        val value = inParametersByName[parameter.name]
            ?: throw IllegalArgumentException("No value for parameter ${parameter.name}")

        return (value as? TypedParameterValue<T>)?.value
            ?: throw IllegalArgumentException("Value for parameter ${parameter.name} is of the wrong type")
    }
}