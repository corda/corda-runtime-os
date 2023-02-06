package net.corda.interop.data

import com.fasterxml.jackson.databind.annotation.JsonDeserialize
import com.fasterxml.jackson.databind.annotation.JsonSerialize

/**
 * A [FacadeRequest] is a request to invoke a [FacadeMethod] on a [Facade].
 *
 * @param facadeId The id of the facade to which the method belongs.
 * @param methodName The name of the method to invoke.
 * @param inParameters The parameter values to pass to the method.
 */
@JsonSerialize(using = FacadeRequestSerializer::class)
@JsonDeserialize(using = FacadeRequestDeserializer::class)
data class FacadeRequest(val facadeId: FacadeId, val methodName: String, val inParameters: List<FacadeParameterValue<*>>)
data class FacadeParameterValue<T : Any>(val parameter: FacadeParameter<T>, val value: T)
data class FacadeParameter<T : Any>(val name: String, val type: FacadeParameterType<T>)