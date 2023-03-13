package net.corda.virtualnode.rpcops.impl.validation

import net.corda.libs.packaging.core.CpiMetadata
import net.corda.libs.virtualnode.endpoints.v1.types.CreateVirtualNodeRequest
import net.corda.virtualnode.HoldingIdentity
import net.corda.virtualnode.VirtualNodeInfo

internal interface VirtualNodeValidationService {
    fun validateVirtualNodeDoesNotExist(holdingIdentity: HoldingIdentity)
    fun validateAndGetGroupId(request: CreateVirtualNodeRequest): String
    fun validateAndGetVirtualNode(virtualNodeShortId: String): VirtualNodeInfo
    fun validateAndGetCpiByChecksum(cpiFileChecksum: String): CpiMetadata
    fun validateCpiUpgradePrerequisites(currentCpi: CpiMetadata, upgradeCpi: CpiMetadata)
}