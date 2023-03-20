package net.corda.interop.service

import net.corda.virtualnode.HoldingIdentity

interface InteropFacadeToFlowMapperService {

    fun getFlowName(
        destinationIdentity: HoldingIdentity,
        facadeId: String,
        facadeName: String
    ): String?
}