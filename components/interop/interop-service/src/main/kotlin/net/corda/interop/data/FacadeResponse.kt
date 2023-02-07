package net.corda.interop.data

import com.fasterxml.jackson.databind.annotation.JsonDeserialize
import com.fasterxml.jackson.databind.annotation.JsonSerialize

//TODO : All facade classed are copied from WEFT project, and in future it can be replaced by facade component

@JsonSerialize(using = FacadeResponseSerializer::class)
@JsonDeserialize(using = FacadeResponseDeserializer::class)
data class FacadeResponse(val facadeId: FacadeId, val methodName: String, val outParameters: List<FacadeParameterValue<*>>)