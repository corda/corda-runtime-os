package net.corda.interop.data

import com.fasterxml.jackson.databind.annotation.JsonDeserialize
import com.fasterxml.jackson.databind.annotation.JsonSerialize

@JsonSerialize(using = FacadeResponseSerializer::class)
@JsonDeserialize(using = FacadeResponseDeserializer::class)
data class FacadeResponse(val facadeId: FacadeId, val methodName: String, val outParameters: List<FacadeParameterValue<*>>)