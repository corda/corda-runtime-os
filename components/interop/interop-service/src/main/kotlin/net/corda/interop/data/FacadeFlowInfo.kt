package net.corda.interop.data

data class FacadeFlowInfo(val facadeId: String, val mapping : List<Mapping>)
data class Mapping(val facadeMethod : String, val flowName : String)
data class FacadeFlowMapping(val facadeFlowMapping : List<FacadeFlowInfo>)