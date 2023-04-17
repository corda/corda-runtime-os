package net.corda.flow.application.services.impl.interop.facade

import com.fasterxml.jackson.databind.annotation.JsonDeserialize
import com.fasterxml.jackson.databind.annotation.JsonSerialize
import net.corda.v5.application.interop.facade.FacadeId
import net.corda.v5.application.interop.facade.FacadeRequest
import net.corda.v5.application.interop.parameters.ParameterType
import net.corda.v5.application.interop.parameters.ParameterTypeLabel

/**
 * A [FacadeRequest] is a request to invoke a [FacadeMethod] on a [Facade].
 *
 * @param facadeId The id of the facade to which the method belongs.
 * @param methodName The name of the method to invoke.
 * @param inParameters The parameter values to pass to the method.
 */
@JsonSerialize(using = FacadeRequestSerializer::class)
@JsonDeserialize(using = FacadeRequestDeserializer::class)
data class FacadeRequestImpl(
    val facadeId: FacadeId,
    val methodName: String,
    val inParameters: List<ParameterTypeLabel>
) : FacadeRequest {

    private val inParametersByName = inParameters.associateBy { it.parameter.name }

    /**
     * Get the value of a parameter by name.
     *
     * @param parameter The parameter to get the value of.
     */
    @Suppress("UNCHECKED_CAST")
    @Override
    override operator fun <T : Any> get(parameter: ParameterType<T>): T {
        val value = inParametersByName[parameter.name]
            ?: throw IllegalArgumentException("No value for parameter ${parameter.name}")

        return (value as? ParameterTypeLabel)?.value
            ?: throw IllegalArgumentException("Value for parameter ${parameter.name} is of the wrong type")
    }
}