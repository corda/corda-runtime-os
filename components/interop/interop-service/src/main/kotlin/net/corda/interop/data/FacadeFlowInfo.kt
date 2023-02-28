package net.corda.interop.data

import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.annotation.JsonSetter
import com.fasterxml.jackson.annotation.Nulls

data class FacadeFlowInfo(
    @JsonSetter(contentNulls = Nulls.FAIL)
    @JsonProperty("facade-id") val facadeId: String,
    @JsonSetter(contentNulls = Nulls.FAIL)
    @JsonProperty("facade-method-mapping") val facadeMethodMapping: List<FacadeMethodMapping>
)

data class FacadeMethodMapping(
    @JsonSetter(contentNulls = Nulls.FAIL)
    @JsonProperty("facade-method") val facadeMethod: String,
    @JsonSetter(contentNulls = Nulls.FAIL)
    @JsonProperty("flow-name") val flowName: String
)

data class FacadeFlowMapping(
    @JsonSetter(contentNulls = Nulls.FAIL)
    @JsonProperty("facade-flow-mapping")
    val facadeFlowMapping: List<FacadeFlowInfo>
)