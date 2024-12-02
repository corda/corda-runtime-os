package net.corda.virtualnode.rest.factories

import net.corda.data.virtualnode.VirtualNodeAsynchronousRequest
import net.corda.libs.virtualnode.endpoints.v1.types.CreateVirtualNodeRequestType
import net.corda.libs.virtualnode.endpoints.v1.types.UpdateVirtualNodeDbRequest
import net.corda.virtualnode.HoldingIdentity

internal interface RequestFactory {
    fun createHoldingIdentity(groupId: String, request: CreateVirtualNodeRequestType): HoldingIdentity

    fun createVirtualNodeRequest(
        holdingIdentity: HoldingIdentity,
        request: CreateVirtualNodeRequestType
    ): VirtualNodeAsynchronousRequest

    fun updateVirtualNodeDbRequest(
        holdingIdentity: HoldingIdentity,
        request: UpdateVirtualNodeDbRequest
    ): VirtualNodeAsynchronousRequest
}
