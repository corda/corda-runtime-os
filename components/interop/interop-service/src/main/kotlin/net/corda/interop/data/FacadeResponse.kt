package net.corda.interop.data

data class FacadeResponse(val facadeId: FacadeId, val methodName: String, val outParameters: List<FacadeParameterValue<*>>)