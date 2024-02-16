package net.corda.virtualnode.rest.factories

import net.corda.data.virtualnode.VirtualNodeAsynchronousRequest
import net.corda.libs.virtualnode.endpoints.v1.types.CreateVirtualNodeRequest
import net.corda.libs.virtualnode.endpoints.v1.types.JsonCreateVirtualNodeRequest
import net.corda.libs.virtualnode.endpoints.v1.types.UpdateVirtualNodeDbRequest
import net.corda.virtualnode.HoldingIdentity

internal interface RequestFactory {
    fun createHoldingIdentityDeprecated(groupId: String, request: CreateVirtualNodeRequest): HoldingIdentity

    fun createHoldingIdentity(groupId: String, request: JsonCreateVirtualNodeRequest): HoldingIdentity

    fun createVirtualNodeRequestDeprecated(
        holdingIdentity: HoldingIdentity,
        request: CreateVirtualNodeRequest
    ): VirtualNodeAsynchronousRequest

    fun createVirtualNodeRequest(
        holdingIdentity: HoldingIdentity,
        request: JsonCreateVirtualNodeRequest
    ): VirtualNodeAsynchronousRequest

    fun updateVirtualNodeDbRequest(
        holdingIdentity: HoldingIdentity,
        request: UpdateVirtualNodeDbRequest
    ): VirtualNodeAsynchronousRequest
}
