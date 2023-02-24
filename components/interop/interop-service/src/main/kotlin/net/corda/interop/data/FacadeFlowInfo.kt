package net.corda.interop.data

import com.fasterxml.jackson.annotation.JsonProperty

data class FacadeFlowInfo(
    @JsonProperty("facade-id") val facadeId: String,
    @JsonProperty("facade-method-mapping") val facadeMethodMapping: List<FacadeMethodMapping>
)

data class FacadeMethodMapping(
    @JsonProperty("facade-method") val facadeMethod: String,
    @JsonProperty("flow-name") val flowName: String
)

data class FacadeFlowMapping(@JsonProperty("facade-flow-mapping") val facadeFlowMapping: List<FacadeFlowInfo>)