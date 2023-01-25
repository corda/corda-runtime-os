package net.corda.virtualnode.rpcops.impl.validation

import net.corda.libs.packaging.core.CpiMetadata
import net.corda.virtualnode.VirtualNodeInfo

interface VirtualNodeValidationService {
    fun validateAndGetVirtualNode(virtualNodeShortId: String): VirtualNodeInfo
    fun validateAndGetCpiByChecksum(cpiFileChecksum: String): CpiMetadata
    fun validateCpiUpgradePrerequisites(currentCpi: CpiMetadata, upgradeCpi: CpiMetadata)
}