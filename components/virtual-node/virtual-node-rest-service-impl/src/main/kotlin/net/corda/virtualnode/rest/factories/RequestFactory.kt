package net.corda.virtualnode.rest.factories

import net.corda.data.virtualnode.VirtualNodeAsynchronousRequest
import net.corda.libs.virtualnode.endpoints.v1.types.CreateVirtualNodeRequest
import net.corda.libs.virtualnode.endpoints.v1.types.UpdateVirtualNodeDbRequest
import net.corda.virtualnode.HoldingIdentity

internal interface RequestFactory {
    fun createHoldingIdentity(groupId: String, request: CreateVirtualNodeRequest): HoldingIdentity

    fun createVirtualNodeRequest(
        holdingIdentity: HoldingIdentity,
        request: CreateVirtualNodeRequest
    ): VirtualNodeAsynchronousRequest

    fun updateVirtualNodeDbRequest(
        holdingIdentity: HoldingIdentity,
        request: UpdateVirtualNodeDbRequest
    ): VirtualNodeAsynchronousRequest
}
